package blocks.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class Mode { CLASSIC, DAILY }

/**
 * Scoring, as in Woodoku (formula from gym-woodoku's "woodoku" score mode, github.com/helpingstar/gym-woodoku):
 * - every placed block (cell) scores 1;
 * - a move that clears c regions (rows, columns, 3x3 boxes) at once adds 28*c + 10*s - 20, where s is the streak:
 *   the number of consecutive moves, this one included, that cleared something. A move with no clear resets s to 0.
 *
 * So a single clear on a fresh streak is +18 on top of the blocks, a double +46, a triple +74; each further move
 * in a row that clears adds another 10.
 */
object Scoring {
    fun clearBonus(combo: Int, streak: Int): Int = if (combo == 0) 0 else 28 * combo + 10 * streak - 20
}

const val TRAY = 3

@Serializable
data class GameState(
    val mode: Mode = Mode.CLASSIC,
    val board: Bits = Bits.EMPTY,
    /** Cells holding a gem (Daily). Always a subset of [board]; a gem is collected when its cell is cleared. */
    val gems: Bits = Bits.EMPTY,
    val gemsTotal: Int = 0,
    /** Shape ids, -1 for a slot already placed. */
    val tray: List<Int> = List(TRAY) { -1 },
    val rng: Rng = Rng(0),
    val score: Int = 0,
    /** Consecutive moves that cleared something (0 after a move with no clear). */
    val streak: Int = 0,
    val moves: Int = 0,
    val blocksPlaced: Int = 0,
    val cellsCleared: Int = 0,
    val regionsCleared: Int = 0,
    val bestCombo: Int = 0,
    val bestStreak: Int = 0,
    /** How many trays of three have been dealt (UI keys the slide-in animation on this). */
    val deals: Int = 0,
    /** Daily puzzle date, ISO yyyy-mm-dd. */
    val date: String? = null,
    val over: Boolean = false,
    val won: Boolean = false,
) {
    val gemsCollected: Int get() = gemsTotal - gems.count
    val trayShapes: List<Shape?> get() = tray.map { if (it < 0) null else Shapes[it] }

    /** Slots whose piece fits somewhere on the board right now. */
    fun playableSlots(): List<Int> = tray.indices.filter { tray[it] >= 0 && Shapes[tray[it]].fitsAnywhere(board) }

    fun canPlace(slot: Int, anchor: Int): Boolean =
        !over && slot in tray.indices && tray[slot] >= 0 && Shapes[tray[slot]].canPlace(board, anchor)

    /** What placing would do, without committing: the cells it fills and the regions that would clear. */
    fun preview(slot: Int, anchor: Int): Preview? {
        if (!canPlace(slot, anchor)) return null
        val mask = Shapes[tray[slot]].masks[anchor]!!
        val after = board or mask
        val regions = Regions.full(after)
        return Preview(mask, regions, regions.fold(Bits.EMPTY) { b, r -> b or r.mask })
    }

    /** Place the piece in [slot] with its top-left at [anchor]. Returns null if the move isn't legal. */
    fun place(slot: Int, anchor: Int): MoveResult? {
        if (!canPlace(slot, anchor)) return null
        val shape = Shapes[tray[slot]]
        val mask = shape.masks[anchor]!!
        val filled = board or mask
        val regions = Regions.full(filled)
        val clearedCells = regions.fold(Bits.EMPTY) { b, r -> b or r.mask }
        val combo = regions.size
        val newStreak = if (combo > 0) streak + 1 else 0
        val bonus = Scoring.clearBonus(combo, newStreak)
        val points = shape.size + bonus
        val gemsTaken = gems and clearedCells
        var s = copy(
            board = filled andNot clearedCells,
            gems = gems andNot clearedCells,
            tray = tray.toMutableList().also { it[slot] = -1 },
            score = score + points,
            streak = newStreak,
            moves = moves + 1,
            blocksPlaced = blocksPlaced + shape.size,
            cellsCleared = cellsCleared + clearedCells.count,
            regionsCleared = regionsCleared + combo,
            bestCombo = maxOf(bestCombo, combo),
            bestStreak = maxOf(bestStreak, newStreak),
        )
        val refilled = s.tray.all { it < 0 }
        if (refilled) s = s.deal()
        s = s.checkOver()
        return MoveResult(s, mask, regions, clearedCells, shape.size, bonus, combo, newStreak, gemsTaken, refilled)
    }

    /** Deal three fresh pieces, each uniformly from the full set. */
    internal fun deal(): GameState {
        var r = rng
        val t = List(TRAY) { val (v, n) = r.nextInt(Shapes.all.size); r = n; v }
        return copy(tray = t, rng = r, deals = deals + 1)
    }

    internal fun checkOver(): GameState = when {
        mode == Mode.DAILY && gemsTotal > 0 && gems.isEmpty -> copy(over = true, won = true)
        playableSlots().isEmpty() -> copy(over = true, won = false)
        else -> this
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(s: String): GameState = json.decodeFromString(serializer(), s)

        fun newClassic(seed: Long): GameState = GameState(rng = Rng(seed)).deal().checkOver()
    }
}

data class Preview(val cells: Bits, val regions: List<Region>, val clearing: Bits)

data class MoveResult(
    val state: GameState,
    val placed: Bits,
    val regions: List<Region>,
    val clearedCells: Bits,
    val blockPoints: Int,
    val bonus: Int,
    val combo: Int,
    val streak: Int,
    val gemsTaken: Bits,
    val refilled: Boolean,
) {
    val points: Int get() = blockPoints + bonus
}
