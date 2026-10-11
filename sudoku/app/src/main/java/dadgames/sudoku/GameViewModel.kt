package dadgames.sudoku

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dadgames.sudoku.core.Difficulty
import dadgames.sudoku.core.GameState
import dadgames.sudoku.core.Generator
import dadgames.sudoku.core.Hint
import dadgames.sudoku.core.MISTAKE_LIMIT
import dadgames.sudoku.core.MoveResult
import dadgames.sudoku.core.Outcome
import dadgames.sudoku.core.PlayOptions
import dadgames.sudoku.core.Puzzle
import dadgames.sudoku.core.Stats
import dadgames.sudoku.core.Status
import dadgames.ui.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.time.LocalDate
import kotlin.random.Random

@Serializable
data class AppSettings(
    val sound: Boolean = false,
    val autoCheck: Boolean = true,
    val mistakeLimit: Boolean = true,
    val highlightAreas: Boolean = true,
    val highlightSame: Boolean = true,
    val highlightDuplicates: Boolean = true,
    val autoRemoveNotes: Boolean = true,
    val showTimer: Boolean = true,
    val lastDifficulty: Difficulty = Difficulty.EASY,
) {
    val play get() = PlayOptions(autoCheck = autoCheck, mistakeLimit = mistakeLimit, autoRemoveNotes = autoRemoveNotes)
}

/** Everything needed to put him back exactly where he was. */
@Serializable
data class Save(val game: GameState, val selected: Int = -1, val notesMode: Boolean = false, val paused: Boolean = false)

enum class Screen { HOME, GAME, DAILY, STATS, SETTINGS, HELP }

/** Line under the board: what just happened, in plain words. */
data class Message(val text: String, val warn: Boolean = false)

/** Shown in the end-of-game panel. */
data class WinInfo(val newBest: Boolean, val previousBest: Long)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app, "sudoku")
    val sound = SoundFx()

    private val loaded = store.load("save", Save.serializer())

    var settings by mutableStateOf(store.load("settings", AppSettings.serializer()) ?: AppSettings())
        private set
    var stats by mutableStateOf(store.load("stats", Stats.serializer()) ?: Stats())
        private set
    var game by mutableStateOf(loaded?.game)
        private set
    var selected by mutableStateOf(loaded?.selected?.takeIf { it in 0..80 })
        private set
    var notesMode by mutableStateOf(loaded?.notesMode ?: false)
        private set
    var paused by mutableStateOf(loaded?.paused ?: false)
        private set
    var message by mutableStateOf<Message?>(null)
        private set
    var hint by mutableStateOf<Hint?>(null)
        private set
    var win by mutableStateOf<WinInfo?>(null)
        private set
    /** Non-null while a puzzle is being made: the text to show. */
    var busy by mutableStateOf<String?>(null)
        private set
    var choosingDifficulty by mutableStateOf(false)
    /** Day picked on the calendar. */
    var dailyDay by mutableStateOf(LocalDate.now())

    /** Killed mid-game: open straight back into the game. [BEST_PRACTICES 12j] */
    private var screenState by mutableStateOf(
        if (store.getBoolean("inGame", false) && loaded?.game?.status == Status.PLAYING) Screen.GAME else Screen.HOME
    )
    var screen: Screen
        get() = screenState
        set(v) {
            screenState = v
            store.putBoolean("inGame", v == Screen.GAME)
            syncClock()
        }

    val canResume get() = game.let { it != null && it.status != Status.WON }

    // ------------------------------------------------------------ clock

    /** Elapsed time shown on screen, updated while the clock runs. */
    var clockMs by mutableLongStateOf(game?.elapsedMs ?: 0)
        private set
    private var runStart: Long? = null
    private var ticker: Job? = null
    private var foreground = true

    /** Wall-clock time, for dates in stats. */
    private fun now() = System.currentTimeMillis()

    /** Monotonic time for the game clock: changing the phone's time can't bend it. */
    private fun tick() = android.os.SystemClock.elapsedRealtime()

    private fun elapsed(): Long = (game?.elapsedMs ?: 0) + (runStart?.let { tick() - it } ?: 0)

    /** Start or stop the clock to match what's on screen. It only runs while he can see the board. */
    private fun syncClock() {
        val g = game
        val run = foreground && screenState == Screen.GAME && !paused && busy == null && g != null && g.status == Status.PLAYING
        if (run && runStart == null) {
            runStart = tick()
            ticker?.cancel()
            ticker = viewModelScope.launch {
                while (isActive) { clockMs = elapsed(); delay(250) }
            }
        } else if (!run && runStart != null) {
            game = g?.withTime(elapsed())
            runStart = null
            ticker?.cancel()
            clockMs = game?.elapsedMs ?: 0
            persist()
        }
    }

    /** Fold the running clock into the game before a move, so the saved time is exact. */
    private fun foldClock() {
        if (runStart != null) {
            val t = tick()
            game = game?.withTime(elapsed())
            runStart = t
        }
    }

    fun setForeground(fg: Boolean) {
        foreground = fg
        syncClock()
        if (!fg) persistNow()
    }

    // ------------------------------------------------------------ saving

    private fun saveData() = game?.let { Save(it, selected ?: -1, notesMode, paused) }

    private fun persist() {
        val s = saveData()
        if (s == null) store.remove("save") else store.save(viewModelScope, "save", Save.serializer(), s)
    }

    private fun persistNow() {
        saveData()?.let { store.saveNow("save", Save.serializer(), it) }
    }

    fun updateSettings(s: AppSettings) {
        settings = s
        store.save(viewModelScope, "settings", AppSettings.serializer(), s)
    }

    private fun saveStats(s: Stats) {
        stats = s
        store.save(viewModelScope, "stats", Stats.serializer(), s)
    }

    /** A game under way stays counted as started, so finishing it can't show "1 win of 0". */
    fun resetStats() = saveStats(Stats.reset(game))

    // ------------------------------------------------------------ puzzle supply

    /** A few ready-made puzzles per level, made in the background so New game is instant. */
    private val pool = (store.load(POOL_KEY, ListSerializer(String.serializer())) ?: emptyList()).mapNotNull { Puzzle.decode(it) }.toMutableList()
    private var refill: Job? = null

    private fun refillPool() {
        if (refill?.isActive == true) return
        refill = viewModelScope.launch {
            while (true) {
                val need = Difficulty.entries.firstOrNull { d -> pool.count { it.difficulty == d } < POOL_SIZE } ?: break
                val p = withContext(Dispatchers.Default) { Generator.generate(need, Random.Default) }
                pool += p
                store.save(viewModelScope, POOL_KEY, ListSerializer(String.serializer()), pool.map { it.encode() })
            }
        }
    }

    /** Daily puzzles made so far (date to encoded puzzle), so opening a day doesn't flash a wait panel. */
    private val dailyCache = HashMap<String, Puzzle>()

    private suspend fun dailyPuzzle(day: LocalDate): Puzzle {
        val key = day.toString()
        dailyCache[key]?.let { return it }
        val p = withContext(Dispatchers.Default) { Generator.daily(key, day.dayOfWeek.value) }
        dailyCache[key] = p
        return p
    }

    init {
        refillPool()
        viewModelScope.launch { dailyPuzzle(LocalDate.now()) }
    }

    /** Show the "One moment" panel only if making the puzzle takes a noticeable time. */
    private fun busyAfterDelay(text: String): Job = viewModelScope.launch {
        delay(400)
        busy = text
    }

    // ------------------------------------------------------------ starting and leaving

    /** Starting something else while a game is under way ends it (counts as not won). */
    private fun abandonCurrent() {
        val g = game ?: return
        if (g.status != Status.WON && g.moves > 0) saveStats(stats.lost(g.withTime(elapsed()), now()))
    }

    private fun begin(g: GameState) {
        runStart = null
        ticker?.cancel()
        game = g
        clockMs = g.elapsedMs
        selected = null
        notesMode = false
        paused = false
        message = null
        hint = null
        win = null
        choosingDifficulty = false
        persist()
        screen = Screen.GAME
    }

    private var generating = false

    fun newGame(d: Difficulty) {
        if (busy != null || generating) return
        abandonCurrent()
        game = null
        updateSettings(settings.copy(lastDifficulty = d))
        val ready = pool.firstOrNull { it.difficulty == d }
        if (ready != null) {
            pool.remove(ready)
            store.save(viewModelScope, POOL_KEY, ListSerializer(String.serializer()), pool.map { it.encode() })
            begin(GameState(ready))
            refillPool()
            return
        }
        choosingDifficulty = false
        val wait = busyAfterDelay("Making a ${d.label.lowercase()} puzzle…")
        generating = true
        viewModelScope.launch {
            val p = withContext(Dispatchers.Default) { Generator.generate(d, Random.Default) }
            wait.cancel(); busy = null; generating = false
            begin(GameState(p))
            refillPool()
        }
    }

    fun playDaily(day: LocalDate) {
        if (busy != null || generating) return
        val key = day.toString()
        val g = game
        if (g != null && g.daily == key && g.status != Status.WON) { resume(); return }
        abandonCurrent()
        game = null
        val replay = key in stats.dailyDone
        val wait = busyAfterDelay("Getting the puzzle for ${day.monthName()} ${day.dayOfMonth}…")
        generating = true
        viewModelScope.launch {
            val p = dailyPuzzle(day)
            wait.cancel(); busy = null; generating = false
            begin(GameState(p, daily = key, replay = replay))
        }
    }

    fun resume() {
        if (game == null) return
        win = null
        screen = Screen.GAME
    }

    fun leaveGame() {
        screen = Screen.HOME
        persist()
    }

    // ------------------------------------------------------------ input

    private val opts get() = settings.play

    fun tapCell(c: Int) {
        val g = game ?: return
        if (g.over || paused) return
        selected = c
        hint = null
        persist()
    }

    fun tapDigit(d: Int) {
        val g = game ?: return
        if (g.over || paused) return
        val c = selected
        if (c == null) { message = Message("Tap a square first, then a number."); return }
        if (notesMode) {
            if (g.values[c] != 0) {
                message = Message(if (g.isGiven(c)) "That number is part of the puzzle." else "Erase this number first to write notes here.")
                return
            }
            apply(g.toggleNote(c, d))
        } else apply(g.enter(c, d, opts))
    }

    fun erase() {
        val g = game ?: return
        val c = selected ?: run { message = Message("Tap a square first, then Erase."); return }
        if (g.over || paused) return
        apply(g.erase(c, opts))
    }

    fun undo() {
        val g = game ?: return
        if (g.over || paused) return
        if (!g.canUndo) { message = Message("Nothing to undo yet."); return }
        apply(g.undoLast())
    }

    fun toggleNotes() {
        if (game?.over != false || paused) return
        notesMode = !notesMode
        message = Message(if (notesMode) "Notes on: numbers you tap are pencilled in small." else "Notes off.")
        persist()
    }

    fun requestHint() {
        val g = game ?: return
        if (g.over || paused) return
        foldClock()
        val start = game!!
        viewModelScope.launch {
            val r = withContext(Dispatchers.Default) { start.hint(selected, opts) }
            if (game !== start) return@launch // he moved on meanwhile
            apply(r)
        }
    }

    fun secondChance() {
        val g = game ?: return
        game = g.secondChance()
        message = Message("Taken back. You have one mistake to spare.")
        persist()
        syncClock()
    }

    fun pause(p: Boolean) {
        if (game?.status != Status.PLAYING) return
        paused = p
        syncClock()
        persist()
    }

    private fun apply(r: MoveResult) {
        val before = game ?: return
        foldClock()
        val g = r.game.withTime(game!!.elapsedMs)
        if (before.moves == 0 && g.moves > 0 && !g.replay) saveStats(stats.started(g.difficulty))
        game = g
        hint = r.hint
        r.hint?.let { selected = it.cell }
        val o = opts
        message = when (r.outcome) {
            Outcome.MISTAKE -> Message(
                if (o.limitActive && g.mistakes == MISTAKE_LIMIT - 1) "That number doesn't fit there. One more mistake ends the game."
                else "That number doesn't fit there.", warn = true,
            )
            Outcome.LOCKED -> Message(if (selected?.let { before.isGiven(it) } == true) "That number is part of the puzzle." else "That number is already right.")
            Outcome.FULL_BUT_WRONG -> Message("Every square is filled, but some numbers aren't right. They're shown in red.", warn = true)
            Outcome.HINT, Outcome.SOLVED -> r.hint?.let { Message(it.reason) }
            Outcome.UNDONE -> null
            else -> null
        }
        when (r.outcome) {
            Outcome.PLACED, Outcome.HINT -> sound.play(Sfx.PLACE, settings.sound)
            Outcome.NOTE, Outcome.ERASED, Outcome.UNDONE -> sound.play(Sfx.SOFT, settings.sound)
            Outcome.MISTAKE, Outcome.OUT_OF_MISTAKES, Outcome.FULL_BUT_WRONG -> sound.play(Sfx.WRONG, settings.sound)
            Outcome.SOLVED -> sound.play(Sfx.WIN, settings.sound)
            else -> {}
        }
        if (r.outcome == Outcome.SOLVED) {
            val prev = stats.of(g.difficulty).bestMs
            saveStats(stats.won(g, now()))
            win = WinInfo(prev == 0L || g.elapsedMs < prev, prev)
            selected = null
        }
        if (g.over) syncClock()
        persist()
    }

    override fun onCleared() {
        persistNow()
        sound.release()
    }

    companion object {
        const val POOL_SIZE = 2
        /** Bump when grading changes, so puzzles made under old levels are dropped. */
        const val POOL_KEY = "pool-v2"
    }
}

fun LocalDate.monthName(): String = month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
fun LocalDate.weekdayName(): String = dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
