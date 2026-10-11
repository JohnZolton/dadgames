package dadgames.sudoku.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Player options that change the rules of play. */
data class PlayOptions(
    /** Wrong numbers show in red right away and count as mistakes. */
    val autoCheck: Boolean = true,
    /** Three mistakes ends the game (only with auto-check). */
    val mistakeLimit: Boolean = true,
    /** Placing a number removes it from pencil notes in the same row, column and box. */
    val autoRemoveNotes: Boolean = true,
) {
    val limitActive get() = autoCheck && mistakeLimit
}

const val MISTAKE_LIMIT = 3

/** Previous contents of one cell, for undo. */
@Serializable
data class CellWas(val cell: Int, val value: Int, val notes: Int)

@Serializable
enum class Status { PLAYING, WON, OUT_OF_MISTAKES }

/** What a move did, for the message line and sounds. */
enum class Outcome { NOTHING, PLACED, NOTE, ERASED, MISTAKE, OUT_OF_MISTAKES, SOLVED, FULL_BUT_WRONG, LOCKED, UNDONE, HINT }

class MoveResult(val game: GameState, val outcome: Outcome, val hint: Hint? = null)

/**
 * One game in progress. Immutable: every move returns a new state, which the app saves.
 * [values] includes the givens. [notes] are pencil-mark bitmasks (bit d = digit d).
 */
@Serializable
data class GameState(
    val puzzle: Puzzle,
    val values: List<Int> = puzzle.givens,
    val notes: List<Int> = List(81) { 0 },
    val mistakes: Int = 0,
    val hints: Int = 0,
    val elapsedMs: Long = 0,
    val undo: List<List<CellWas>> = emptyList(),
    val status: Status = Status.PLAYING,
    /** Date (yyyy-mm-dd) for a daily challenge, else null. */
    val daily: String? = null,
    /** Inputs made so far (a game with none isn't counted in stats). */
    val moves: Int = 0,
    /** Set when the board was filled with wrong numbers and auto-check is off: show them in red. */
    val revealWrong: Boolean = false,
) {
    val difficulty get() = puzzle.difficulty
    fun isGiven(c: Int) = puzzle.givens[c] != 0
    fun isWrong(c: Int) = values[c] != 0 && values[c] != puzzle.solution[c]
    val solved get() = values == puzzle.solution
    val over get() = status != Status.PLAYING

    /** How many of digit d are on the board (only right ones when auto-check is on, so it gives nothing away otherwise). */
    fun placed(d: Int, autoCheck: Boolean): Int =
        (0 until 81).count { values[it] == d && (!autoCheck || values[it] == puzzle.solution[it]) }

    fun remaining(d: Int, autoCheck: Boolean) = (9 - placed(d, autoCheck)).coerceAtLeast(0)

    /** All nine of digit d are in, and right: the number pad can drop it. */
    fun digitDone(d: Int) = placed(d, autoCheck = true) == 9

    /** Cells whose digit repeats in a row, column or box. */
    fun conflicts(): Set<Int> {
        val out = HashSet<Int>()
        for (c in 0 until 81) {
            val v = values[c]
            if (v != 0 && Board.peers[c].any { values[it] == v }) out += c
        }
        return out
    }

    /** A filled cell can't be changed: givens always, right answers when auto-check is on. */
    fun locked(c: Int, o: PlayOptions) = isGiven(c) || (o.autoCheck && values[c] == puzzle.solution[c])

    // ------------------------------------------------------------ moves

    fun enter(c: Int, d: Int, o: PlayOptions): MoveResult {
        if (over || d !in 1..9) return MoveResult(this, Outcome.NOTHING)
        if (locked(c, o)) return MoveResult(this, Outcome.LOCKED)
        if (values[c] == d) return MoveResult(this, Outcome.NOTHING)
        val vals = values.toMutableList()
        val nts = notes.toMutableList()
        val was = mutableListOf(CellWas(c, values[c], notes[c]))
        vals[c] = d
        nts[c] = 0
        if (o.autoRemoveNotes) for (p in Board.peers[c]) if (nts[p] and Board.bit(d) != 0) {
            was += CellWas(p, vals[p], nts[p])
            nts[p] = nts[p] and Board.bit(d).inv()
        }
        val wrong = d != puzzle.solution[c]
        val newMistakes = if (wrong && o.autoCheck) mistakes + 1 else mistakes
        var g = copy(values = vals, notes = nts, undo = (undo + listOf(was)).takeLast(UNDO_DEPTH), mistakes = newMistakes, moves = moves + 1, revealWrong = false)
        val outcome = when {
            g.solved -> { g = g.copy(status = Status.WON); Outcome.SOLVED }
            wrong && o.limitActive && newMistakes >= MISTAKE_LIMIT -> { g = g.copy(status = Status.OUT_OF_MISTAKES); Outcome.OUT_OF_MISTAKES }
            wrong && o.autoCheck -> Outcome.MISTAKE
            vals.all { it != 0 } -> { g = g.copy(revealWrong = true); Outcome.FULL_BUT_WRONG }
            else -> Outcome.PLACED
        }
        return MoveResult(g, outcome)
    }

    fun toggleNote(c: Int, d: Int): MoveResult {
        if (over || d !in 1..9 || values[c] != 0) return MoveResult(this, Outcome.NOTHING)
        val nts = notes.toMutableList()
        nts[c] = nts[c] xor Board.bit(d)
        return MoveResult(copy(notes = nts, undo = (undo + listOf(listOf(CellWas(c, 0, notes[c])))).takeLast(UNDO_DEPTH), moves = moves + 1), Outcome.NOTE)
    }

    fun erase(c: Int, o: PlayOptions): MoveResult {
        if (over) return MoveResult(this, Outcome.NOTHING)
        if (values[c] != 0 && locked(c, o)) return MoveResult(this, Outcome.LOCKED)
        if (values[c] == 0 && notes[c] == 0) return MoveResult(this, Outcome.NOTHING)
        val vals = values.toMutableList(); vals[c] = 0
        val nts = notes.toMutableList(); nts[c] = 0
        return MoveResult(copy(values = vals, notes = nts, undo = (undo + listOf(listOf(CellWas(c, values[c], notes[c])))).takeLast(UNDO_DEPTH), moves = moves + 1, revealWrong = false), Outcome.ERASED)
    }

    val canUndo get() = !over && undo.isNotEmpty()

    /** Take back the last input. Mistakes already made still count, as in the common apps. */
    fun undoLast(): MoveResult {
        if (!canUndo) return MoveResult(this, Outcome.NOTHING)
        val vals = values.toMutableList()
        val nts = notes.toMutableList()
        for (w in undo.last().asReversed()) { vals[w.cell] = w.value; nts[w.cell] = w.notes }
        return MoveResult(copy(values = vals, notes = nts, undo = undo.dropLast(1), moves = moves + 1, revealWrong = false), Outcome.UNDONE)
    }

    /** After three mistakes: take back the last move and keep going with one mistake to spare. */
    fun secondChance(): GameState {
        if (status != Status.OUT_OF_MISTAKES) return this
        val g = copy(status = Status.PLAYING).undoLast().game
        return g.copy(mistakes = MISTAKE_LIMIT - 1)
    }

    /** Fill one cell (or fix a wrong one) and explain why. */
    fun hint(selected: Int?, o: PlayOptions): MoveResult {
        if (over) return MoveResult(this, Outcome.NOTHING)
        val h = Hints.find(values.toIntArray(), puzzle.solution.toIntArray(), selected) ?: return MoveResult(this, Outcome.NOTHING)
        val r = this.let { g ->
            // A hint is never a mistake, and may replace a locked-looking wrong number.
            val vals = g.values.toMutableList()
            val nts = g.notes.toMutableList()
            val was = mutableListOf(CellWas(h.cell, vals[h.cell], nts[h.cell]))
            vals[h.cell] = h.digit
            nts[h.cell] = 0
            if (o.autoRemoveNotes) for (p in Board.peers[h.cell]) if (nts[p] and Board.bit(h.digit) != 0) {
                was += CellWas(p, vals[p], nts[p]); nts[p] = nts[p] and Board.bit(h.digit).inv()
            }
            g.copy(values = vals, notes = nts, hints = hints + 1, undo = (undo + listOf(was)).takeLast(UNDO_DEPTH), moves = moves + 1, revealWrong = false)
        }
        return if (r.solved) MoveResult(r.copy(status = Status.WON), Outcome.SOLVED, h) else MoveResult(r, Outcome.HINT, h)
    }

    fun withTime(ms: Long) = copy(elapsedMs = ms)

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val UNDO_DEPTH = 300
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(s: String): GameState = json.decodeFromString(serializer(), s)
    }
}
