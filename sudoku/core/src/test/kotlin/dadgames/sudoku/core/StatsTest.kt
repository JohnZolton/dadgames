package dadgames.sudoku.core

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsTest {
    private fun won(ms: Long, d: Difficulty = Difficulty.EASY, daily: String? = null) =
        GameState(Fixtures.classic.copy(difficulty = d), status = Status.WON, elapsedMs = ms, daily = daily)

    @Test fun recordsWinsTimesAndStreaks() {
        var s = Stats()
        s = s.started(Difficulty.EASY).won(won(300_000), 1)
        s = s.started(Difficulty.EASY).won(won(200_000), 2)
        s = s.started(Difficulty.EASY).lost(won(50_000).copy(status = Status.PLAYING), 3)
        s = s.started(Difficulty.EASY).won(won(400_000), 4)
        val e = s.of(Difficulty.EASY)
        assertEquals(4, e.games)
        assertEquals(3, e.wins)
        assertEquals(75, e.winRate)
        assertEquals(200_000, e.bestMs)
        assertEquals(300_000, e.averageMs)
        assertEquals(1, e.streak)
        assertEquals(2, e.bestStreak)
        assertEquals(listOf(4L, 3L, 2L, 1L), s.recent.map { it.time })
        assertEquals(LevelStats(), s.of(Difficulty.HARD))
        assertEquals(4, s.totalGames)
    }

    @Test fun levelsAreSeparate() {
        var s = Stats().started(Difficulty.HARD).won(won(1000, Difficulty.HARD), 1)
        s = s.started(Difficulty.EASY).lost(won(1, Difficulty.EASY), 2)
        assertEquals(1, s.of(Difficulty.HARD).streak)
        assertEquals(0, s.of(Difficulty.EASY).wins)
    }

    @Test fun dailyMarkedDone() {
        val s = Stats().started(Difficulty.EASY).won(won(1000, daily = "2026-10-11"), 1)
        assertEquals(setOf("2026-10-11"), s.dailyDone)
    }

    @Test fun recentIsCapped() {
        var s = Stats()
        repeat(40) { s = s.started(Difficulty.EASY).won(won(1000L + it), it.toLong()) }
        assertEquals(Stats.RECENT, s.recent.size)
        assertEquals(39L, s.recent.first().time)
    }

    @Test fun timeFormat() {
        assertEquals("0:05", formatTime(5_400))
        assertEquals("12:34", formatTime(754_000))
        assertEquals("1:02:03", formatTime(3_723_000))
    }
}
