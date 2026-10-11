package dadgames.blocks

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import blocks.core.Bits
import blocks.core.Bot
import blocks.core.Daily
import blocks.core.GameState
import blocks.core.Mode
import blocks.core.Move
import blocks.core.MoveResult
import dadgames.ui.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

enum class Screen { HOME, GAME, DAILY, SETTINGS, HELP, STATS }

/** One move's visual effects, keyed by [id] so the board replays them once. */
data class MoveFx(
    val id: Int,
    val placed: Bits,
    val cleared: Bits,
    val points: Int,
    val combo: Int,
    val streak: Int,
    val gems: Bits,
    val time: Long = System.currentTimeMillis(),
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app, "blocks")

    var screen by mutableStateOf(Screen.HOME)
    /** Where Settings / How to play go back to. */
    var returnTo = Screen.HOME

    var settings by mutableStateOf(store.load("settings", AppSettings.serializer()) ?: AppSettings())
        private set
    var highScores by mutableStateOf(store.load("high", HighScores.serializer()) ?: HighScores.seeded)
        private set
    var stats by mutableStateOf(store.load("stats", Stats.serializer()) ?: Stats())
        private set
    var daily by mutableStateOf(store.load("dailyProgress", DailyProgress.serializer()) ?: DailyProgress())
        private set

    var classic by mutableStateOf(store.load("classic", GameState.serializer()))
        private set
    var dailyGame by mutableStateOf(store.load("dailyGame", GameState.serializer()))
        private set
    var playing by mutableStateOf(store.load("playing", Mode.serializer()) ?: Mode.CLASSIC)
        private set

    val game: GameState? get() = if (playing == Mode.CLASSIC) classic else dailyGame

    var fx by mutableStateOf<MoveFx?>(null)
        private set
    var hint by mutableStateOf<Move?>(null)
        private set
    var thinking by mutableStateOf(false)
        private set
    /** Building a daily puzzle (checking it's solvable takes a moment). */
    var preparing by mutableStateOf(false)
        private set
    /** Selected day on the calendar. */
    var calendarDate by mutableStateOf(today())

    /** Bests as they stood when the current game started, for "New best" banners. */
    var startToday by mutableStateOf(highScores.todayBest(today()))
        private set
    var startEver by mutableStateOf(highScores.ever)
        private set

    private var fxId = 0
    private var hintJob: Job? = null
    private var sound: Sound? = null

    init {
        if (settings.sound) sound = Sound()
    }

    fun today(): LocalDate = LocalDate.now()

    val canResume: Boolean get() = classic.let { it != null && !it.over && it.moves > 0 }

    // ------------------------------------------------------------ games

    fun newGame() {
        recordUnfinished()
        val g = GameState.newClassic(Random.nextLong())
        classic = g
        store.save(viewModelScope, "classic", GameState.serializer(), g)
        startPlaying(Mode.CLASSIC)
    }

    fun resume() {
        if (classic == null || classic!!.over) return newGame()
        startPlaying(Mode.CLASSIC)
    }

    private fun startPlaying(mode: Mode) {
        playing = mode
        store.save(viewModelScope, "playing", Mode.serializer(), mode)
        fx = null
        hint = null
        startToday = highScores.todayBest(today())
        startEver = highScores.ever
        screen = Screen.GAME
    }

    /** Play (or continue) the daily puzzle for [date]. */
    fun playDaily(date: LocalDate, restart: Boolean = false) {
        val key = date.toString()
        val cur = dailyGame
        if (!restart && cur != null && cur.date == key && !cur.over) return startPlaying(Mode.DAILY)
        if (preparing) return
        preparing = true
        viewModelScope.launch {
            val g = withContext(Dispatchers.Default) { Daily.generate(key) }
            dailyGame = g
            store.save(viewModelScope, "dailyGame", GameState.serializer(), g)
            preparing = false
            startPlaying(Mode.DAILY)
        }
    }

    fun leaveGame() {
        hintJob?.cancel()
        hint = null
        screen = if (playing == Mode.DAILY) Screen.DAILY else Screen.HOME
    }

    /** Commit a placement. Returns false if it isn't legal (the piece then goes back to the tray). */
    fun place(slot: Int, anchor: Int): Boolean {
        val g = game ?: return false
        val r = g.place(slot, anchor) ?: return false
        hintJob?.cancel()
        hint = null
        thinking = false
        fx = MoveFx(++fxId, r.placed, r.clearedCells, r.points, r.combo, r.streak, r.gemsTaken)
        if (settings.sound) {
            if (r.combo > 0) sound?.clear(r.combo) else sound?.place()
        }
        store(r.state)
        if (r.state.over) finished(r)
        return true
    }

    private fun store(g: GameState) {
        if (g.mode == Mode.CLASSIC) {
            classic = g
            store.save(viewModelScope, "classic", GameState.serializer(), g)
        } else {
            dailyGame = g
            store.save(viewModelScope, "dailyGame", GameState.serializer(), g)
        }
    }

    private fun finished(r: MoveResult) {
        val g = r.state
        if (g.mode == Mode.CLASSIC) recordClassic(g)
        else if (g.won) {
            val d = DailyProgress(daily.won + g.date!!)
            daily = d
            store.save(viewModelScope, "dailyProgress", DailyProgress.serializer(), d)
            saveStats(stats.copy(dailyWins = stats.dailyWins + 1))
        }
    }

    private fun recordClassic(g: GameState) {
        val d = today()
        val record = when {
            g.score > highScores.ever -> "ever"
            g.score > highScores.todayBest(d) -> "today"
            else -> ""
        }
        val hs = highScores.record(g.score, d)
        highScores = hs
        store.save(viewModelScope, "high", HighScores.serializer(), hs)
        val rec = GameRecord(System.currentTimeMillis(), g.score, g.moves, g.bestCombo, record)
        saveStats(
            stats.copy(
                played = stats.played + 1,
                totalScore = stats.totalScore + g.score,
                blocksPlaced = stats.blocksPlaced + g.blocksPlaced,
                cellsCleared = stats.cellsCleared + g.cellsCleared,
                regionsCleared = stats.regionsCleared + g.regionsCleared,
                bestCombo = maxOf(stats.bestCombo, g.bestCombo),
                bestStreak = maxOf(stats.bestStreak, g.bestStreak),
                recent = (listOf(rec) + stats.recent).take(20),
            )
        )
    }

    /** Starting a new game over an unfinished one still counts the score you had. */
    private fun recordUnfinished() {
        val g = classic ?: return
        if (g.over || g.moves == 0) return
        recordClassic(g)
    }

    fun requestHint() {
        val g = game ?: return
        if (g.over || thinking) return
        thinking = true
        hintJob = viewModelScope.launch {
            val m = withContext(Dispatchers.Default) { Bot().choose(g) }
            if (game == g) hint = m
            thinking = false
        }
    }

    // ------------------------------------------------------------ settings & stats

    fun updateSettings(s: AppSettings) {
        settings = s
        if (s.sound && sound == null) sound = Sound()
        store.save(viewModelScope, "settings", AppSettings.serializer(), s)
    }

    private fun saveStats(s: Stats) {
        stats = s
        store.save(viewModelScope, "stats", Stats.serializer(), s)
    }

    fun resetStats() = saveStats(Stats())

    fun resetHighScores() {
        highScores = HighScores()
        store.save(viewModelScope, "high", HighScores.serializer(), highScores)
    }

    /** Remember whether he was on the board, so a relaunch after Android kills the app lands back there. */
    fun rememberScreen() {
        val inGame = screen == Screen.GAME || (screen in setOf(Screen.SETTINGS, Screen.HELP) && returnTo == Screen.GAME)
        store.putBoolean("inGame", inGame)
    }

    fun restoreScreen() {
        if (!store.getBoolean("inGame", false)) return
        val g = game ?: return
        if (!g.over) startPlaying(playing)
    }

    fun openSettings(from: Screen) { returnTo = from; screen = Screen.SETTINGS }
    fun openHelp(from: Screen) { returnTo = from; screen = Screen.HELP }

    override fun onCleared() {
        sound?.release()
    }
}
