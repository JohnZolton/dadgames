package dadgames.blocks

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Serializable
data class AppSettings(
    val sound: Boolean = false,
    val haptics: Boolean = true,
)

/** A best score and the period it belongs to (a date, the Monday of a week, "2026-10", "2026"). */
@Serializable
data class PeriodBest(val period: String, val score: Int)

object Periods {
    fun day(d: LocalDate) = d.toString()
    fun week(d: LocalDate) = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
    fun month(d: LocalDate) = d.toString().substring(0, 7)
    fun year(d: LocalDate) = d.year.toString()
}

/** My High Scores: Today / This week / This month / This year / Best ever, like Woodoku's Classic tab. */
@Serializable
data class HighScores(
    val today: PeriodBest = PeriodBest("", 0),
    val week: PeriodBest = PeriodBest("", 0),
    val month: PeriodBest = PeriodBest("", 0),
    val year: PeriodBest = PeriodBest("", 0),
    val ever: Int = 0,
) {
    fun todayBest(d: LocalDate) = if (today.period == Periods.day(d)) today.score else 0
    fun weekBest(d: LocalDate) = if (week.period == Periods.week(d)) week.score else 0
    fun monthBest(d: LocalDate) = if (month.period == Periods.month(d)) month.score else 0
    fun yearBest(d: LocalDate) = if (year.period == Periods.year(d)) year.score else 0

    fun record(score: Int, d: LocalDate) = HighScores(
        today = PeriodBest(Periods.day(d), maxOf(todayBest(d), score)),
        week = PeriodBest(Periods.week(d), maxOf(weekBest(d), score)),
        month = PeriodBest(Periods.month(d), maxOf(monthBest(d), score)),
        year = PeriodBest(Periods.year(d), maxOf(yearBest(d), score)),
        ever = maxOf(ever, score),
    )

    companion object {
        /** His Woodoku records, read off his phone on 2026-10-11 (research/README.md). */
        val seeded: HighScores = LocalDate.of(2026, 10, 11).let { d ->
            HighScores(
                today = PeriodBest(Periods.day(d), 750),
                week = PeriodBest(Periods.week(d), 1380),
                month = PeriodBest(Periods.month(d), 1380),
                year = PeriodBest(Periods.year(d), 2158),
                ever = 2447,
            )
        }
    }
}

@Serializable
data class GameRecord(
    val time: Long,
    val score: Int,
    val moves: Int,
    val bestCombo: Int,
    /** "ever", "today" or "" for a game that set no record. */
    val record: String = "",
)

@Serializable
data class Stats(
    val played: Int = 0,
    val totalScore: Long = 0,
    val blocksPlaced: Long = 0,
    val cellsCleared: Long = 0,
    val regionsCleared: Long = 0,
    val bestCombo: Int = 0,
    val bestStreak: Int = 0,
    val dailyWins: Int = 0,
    /** Newest first, capped at 20. */
    val recent: List<GameRecord> = emptyList(),
) {
    val average: Int get() = if (played == 0) 0 else (totalScore / played).toInt()
}

/** Solved daily puzzles, by ISO date. */
@Serializable
data class DailyProgress(val won: Set<String> = emptySet()) {
    fun winsIn(month: String) = won.count { it.startsWith(month) }
}
