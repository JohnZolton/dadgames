package solitaire.core

import kotlinx.serialization.Serializable

/**
 * All scoring lives here; the engine only calls [ScoreRules]. Two rule sets:
 * - [ScoringMode.OLD_APP] (default): exactly how dad's old app (com.dna.solitaireapp 28.0.4) scores,
 *   decompiled from its APK. Reproduces all 10 of his top scores. [research/solitaire.md]
 * - [ScoringMode.WINDOWS]: Windows standard scoring, as an option.
 */
@Serializable
enum class ScoringMode(val label: String) {
    OLD_APP("Like your old app"),
    WINDOWS("Windows"),
    ;

    val rules: ScoreRules get() = when (this) { OLD_APP -> OldAppScoring; WINDOWS -> WindowsScoring }
}

interface ScoreRules {
    /** Points for moving a card from one kind of pile to another (a turned-over card is extra). */
    fun forMove(from: PileType, to: PileType): Int
    /** Points for a column card being turned face up. */
    fun forTurnOver(): Int
    /** Points for turning the waste back over onto the stock; [recycles] counts this one. */
    fun forRecycle(draw: Int, recycles: Int): Int
    /** Extra points (≤ 0) each undo costs, after the move's own points are taken back. */
    fun undoCost(): Int
    /** Points (≤ 0) for every 10 seconds of play; 0 for none. */
    fun perTenSeconds(): Int
    /** Win bonus for a base [score] reached in [seconds]. */
    fun winBonus(score: Int, seconds: Long): Int
}

object Scoring {
    const val TIME_STEP_MS = 10_000L

    /** Apply points, never going below zero (each deduction is clamped). */
    fun add(score: Int, points: Int): Int = maxOf(0, score + points)
}

/** Dad's old app, decompiled (ScoreManager.CountScore / CalculateGameCompleteScore / OnDeckPass). */
object OldAppScoring : ScoreRules {
    const val WASTE_TO_TABLEAU = 45
    const val WASTE_TO_FOUNDATION = 105
    const val TABLEAU_TO_FOUNDATION = 60
    const val FOUNDATION_TO_TABLEAU = -75
    const val TURN_OVER = 25
    const val UNDO = -25
    /** Every deck pass in Draw 1; in Draw 3 the first three passes are free. */
    const val DECK_PASS = -200
    const val FREE_DRAW3_PASSES = 3
    const val BONUS_FACTOR = 20

    override fun forMove(from: PileType, to: PileType): Int = when {
        from == PileType.WASTE && to == PileType.TABLEAU -> WASTE_TO_TABLEAU
        from == PileType.WASTE && to == PileType.FOUNDATION -> WASTE_TO_FOUNDATION
        // Auto-complete sends stock cards straight up: the same as drawing them and playing from the waste.
        from == PileType.STOCK && to == PileType.FOUNDATION -> WASTE_TO_FOUNDATION
        from == PileType.TABLEAU && to == PileType.FOUNDATION -> TABLEAU_TO_FOUNDATION
        from == PileType.FOUNDATION && to == PileType.TABLEAU -> FOUNDATION_TO_TABLEAU
        else -> 0
    }

    override fun forTurnOver() = TURN_OVER
    override fun forRecycle(draw: Int, recycles: Int) = if (draw == 1 || recycles > FREE_DRAW3_PASSES) DECK_PASS else 0
    override fun undoCost() = UNDO
    override fun perTenSeconds() = 0

    /** (20 × score) / whole seconds, integer division; no cap. */
    override fun winBonus(score: Int, seconds: Long): Int = (BONUS_FACTOR.toLong() * score / maxOf(seconds, 1L)).toInt()
}

/** Windows standard scoring with the 700000 / seconds time bonus (wins over 30 s). */
object WindowsScoring : ScoreRules {
    const val WASTE_TO_TABLEAU = 5
    const val TO_FOUNDATION = 10
    const val TURN_OVER = 5
    const val FOUNDATION_TO_TABLEAU = -15
    const val RECYCLE_DRAW1 = -100
    /** Draw 3: the first three turn-overs are free, then −20 each (as Windows). */
    const val RECYCLE_DRAW3 = -20
    const val FREE_DRAW3_RECYCLES = 3
    const val TIME_PENALTY = -2

    override fun forMove(from: PileType, to: PileType): Int = when {
        to == PileType.FOUNDATION -> TO_FOUNDATION
        from == PileType.WASTE -> WASTE_TO_TABLEAU
        from == PileType.FOUNDATION -> FOUNDATION_TO_TABLEAU
        else -> 0
    }

    override fun forTurnOver() = TURN_OVER
    override fun forRecycle(draw: Int, recycles: Int) = when {
        draw == 1 -> RECYCLE_DRAW1
        recycles > FREE_DRAW3_RECYCLES -> RECYCLE_DRAW3
        else -> 0
    }
    override fun undoCost() = 0
    override fun perTenSeconds() = TIME_PENALTY
    override fun winBonus(score: Int, seconds: Long): Int = if (seconds <= 30) 0 else (700_000 / seconds).toInt()
}
