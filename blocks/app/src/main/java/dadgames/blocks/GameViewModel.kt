package dadgames.blocks

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

@kotlinx.serialization.Serializable
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

    /** Back stack; HOME is always at the bottom and the last entry is showing. [BEST_PRACTICES #13e] */
    private val stack = mutableStateListOf(Screen.HOME)
    val screen: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun go(s: Screen) {
        if (screen != s) stack.add(s)
        saveStack()
    }

    fun back() {
        if (screen == Screen.GAME) { hintJob?.cancel(); hint = null }
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        saveStack()
    }

    /** Back to the home screen, dropping everything above it. */
    fun home() {
        stack.clear(); stack.add(Screen.HOME)
        saveStack()
    }

    private fun saveStack() = store.save(viewModelScope, "stack", kotlinx.serialization.builtins.ListSerializer(Screen.serializer()), stack.toList())

    /** Relaunch after Android killed the app: same screens, minus a game that no longer exists. [BEST_PRACTICES #12j] */
    fun restoreStack() {
        val saved = store.load("stack", kotlinx.serialization.builtins.ListSerializer(Screen.serializer())) ?: return
        val ok = saved.filter { it != Screen.HOME && (it != Screen.GAME || game?.over == false) }
        stack.clear(); stack.add(Screen.HOME); stack.addAll(ok)
        if (Screen.GAME in ok) refreshStartBests()
    }

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
        refreshStartBests()
        go(Screen.GAME)
    }

    private fun refreshStartBests() {
        startToday = highScores.todayBest(today())
        startEver = highScores.ever
    }

    /** A daily other than [date] has moves in it and isn't finished: starting [date] would throw it away. */
    fun dailyAtRisk(date: LocalDate): GameState? =
        dailyGame?.takeIf { it.date != date.toString() && !it.over && it.moves > 0 }

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

    fun leaveGame() = back()

    /** End-of-daily "Calendar": back to the calendar, whichever way we came. */
    fun toCalendar() {
        back()
        if (screen != Screen.DAILY) go(Screen.DAILY)
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
        else if (g.won && g.date!! !in daily.won) {
            // Each date counts once, however often it's replayed. [BEST_PRACTICES #13g]
            val d = DailyProgress(daily.won + g.date!!)
            daily = d
            store.save(viewModelScope, "dailyProgress", DailyProgress.serializer(), d)
            saveStats(stats.copy(dailyWins = stats.dailyWins + 1))
        }
    }

    private fun recordClassic(g: GameState, abandoned: Boolean = false) {
        val d = today()
        val record = when {
            g.score > highScores.ever -> "ever"
            g.score > highScores.todayBest(d) -> "today"
            else -> ""
        }
        val hs = highScores.record(g.score, d)
        highScores = hs
        store.save(viewModelScope, "high", HighScores.serializer(), hs)
        val rec = GameRecord(System.currentTimeMillis(), g.score, g.moves, g.bestCombo, record, abandoned)
        // An unfinished game still counts for high scores, blocks and records, but not for the average.
        saveStats(
            stats.copy(
                played = stats.played + if (abandoned) 0 else 1,
                totalScore = stats.totalScore + if (abandoned) 0 else g.score,
                abandoned = stats.abandoned + if (abandoned) 1 else 0,
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
        recordClassic(g, abandoned = true)
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

    /** High scores including the classic game in progress, for the home screen. */
    val highScoresLive: HighScores
        get() = classic?.takeIf { !it.over && it.moves > 0 }?.let { highScores.record(it.score, today()) } ?: highScores

    override fun onCleared() {
        sound?.release()
    }
}
