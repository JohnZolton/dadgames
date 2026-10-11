package dadgames.solitaire

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dadgames.cards.Card
import dadgames.ui.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import solitaire.core.Daily
import solitaire.core.DailyProgress
import solitaire.core.Hint
import solitaire.core.Klondike
import solitaire.core.Loc
import solitaire.core.Move
import solitaire.core.PileType
import solitaire.core.Stats
import solitaire.core.autoMove
import solitaire.core.hint
import java.time.LocalDate
import kotlin.random.Random

enum class Screen { HOME, GAME, SETTINGS, STATS, DAILY }

/** What the end-of-game panel shows besides the game itself. */
data class WinInfo(val rank: Int?, val newBest: Boolean)

/** A tap that had nowhere to go: the cards give a little shake. */
data class Shake(val cards: List<Card>, val id: Int)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app, "solitaire")
    private val sounds = Sounds(app)


    var settings by mutableStateOf(store.load(K_SETTINGS, AppSettings.serializer()) ?: AppSettings())
        private set
    var stats by mutableStateOf(store.load(K_STATS, Stats.serializer()) ?: Stats.seeded)
        private set
    var daily by mutableStateOf(store.load(K_DAILY, DailyProgress.serializer()) ?: DailyProgress())
        private set
    var game by mutableStateOf(store.load(K_GAME, Klondike.serializer()))
        private set

    /** Back where he left off after Android kills the app: the game, or the menu screen he was on. [BEST_PRACTICES #12j] */
    var screen by mutableStateOf(
        store.load(K_SCREEN, String.serializer())
            ?.let { runCatching { Screen.valueOf(it) }.getOrNull() }
            ?.let { if (it == Screen.GAME && game.let { g -> g == null || g.won }) Screen.HOME else it }
            ?: Screen.HOME
    )
        private set
    /** Screens to go back to. Home and the game reset it. */
    private val stack = ArrayList<Screen>().apply { if (screen != Screen.HOME) add(Screen.HOME) }

    private fun rememberScreen() = store.save(viewModelScope, K_SCREEN, String.serializer(), screen.name)

    var hint by mutableStateOf<Hint?>(null)
        private set
    var noMoves by mutableStateOf(false)
        private set
    var shake by mutableStateOf<Shake?>(null)
        private set
    private var shakeId = 0
    /** Seconds on the clock, updated every second (the game state itself only every 10 s, for the penalty). */
    var clockSec by mutableLongStateOf(game?.seconds ?: 0)
        private set
    var celebrating by mutableStateOf(false)
        private set
    var autoCompleting by mutableStateOf(false)
        private set
    var winInfo by mutableStateOf<WinInfo?>(null)
        private set
    /** Date key of a daily deal being prepared (solver running). */
    var preparing by mutableStateOf<String?>(null)
        private set
    /** Bumps on every new deal so the board can play the deal animation. */
    var dealCount by mutableIntStateOf(0)
        private set
    private var dealShown = 0

    /** True once per new deal: the board plays the deal animation the first time it shows it. */
    fun takeDeal(): Boolean = (dealCount > dealShown).also { dealShown = dealCount }

    val canResume: Boolean get() = game.let { it != null && !it.won }

    init {
        if (settings.sounds) sounds.warmUp()
        viewModelScope.launch { tickLoop() }
    }

    // ------------------------------------------------------------ clock

    private var foreground = false
    private var runningSince: Long? = null
    private var baseMs = 0L

    private fun clockNow(): Long = baseMs + (runningSince?.let { SystemClock.elapsedRealtime() - it } ?: 0L)

    private val shouldRun: Boolean
        get() = foreground && screen == Screen.GAME && game.let { it != null && it.started && !it.won }

    /** Start or stop the clock to match what's on screen. Call after anything that could change it. */
    fun syncClock() {
        val g = game
        if (shouldRun && runningSince == null) {
            baseMs = g!!.elapsedMs
            runningSince = SystemClock.elapsedRealtime()
        } else if (!shouldRun && runningSince != null) {
            val ms = clockNow()
            runningSince = null
            if (g != null && !g.won) { game = g.tick(ms); clockSec = game!!.seconds; saveGame() }
        }
    }

    private suspend fun tickLoop() {
        var lastSave = 0L
        while (viewModelScope.isActive) {
            delay(200)
            val g = game ?: continue
            if (runningSince == null) continue
            val ms = clockNow()
            clockSec = ms / 1000
            if (ms / 10_000 != g.elapsedMs / 10_000) game = g.tick(ms)
            if (ms - lastSave >= 5000 || ms < lastSave) { lastSave = ms; saveGame(game!!.tick(ms)) }
        }
    }

    fun onForeground(fg: Boolean) {
        foreground = fg
        syncClock()
        if (!fg) game?.let { store.saveNow(K_GAME, Klondike.serializer(), it) }
    }

    fun go(s: Screen) {
        when (s) {
            Screen.HOME -> stack.clear()
            Screen.GAME -> { stack.clear(); stack.add(Screen.HOME) }
            else -> if (s != screen) stack.add(screen)
        }
        screen = s
        rememberScreen()
        syncClock()
    }

    fun back() {
        var s = stack.removeLastOrNull() ?: Screen.HOME
        if (s == Screen.GAME && game == null) s = Screen.HOME
        screen = s
        rememberScreen()
        syncClock()
    }

    // ------------------------------------------------------------ games

    fun newGame() = start(Klondike.deal(Random.nextLong(), settings.draw))

    fun resume() {
        if (!canResume) return newGame()
        go(Screen.GAME)
    }

    private fun start(g: Klondike) {
        recordAbandon()
        runningSince = null
        game = g
        clockSec = 0
        hint = null; noMoves = false; celebrating = false; autoCompleting = false; winInfo = null
        dealCount++
        saveGame()
        go(Screen.GAME)
    }

    /** Play (or resume) the daily challenge for [key] ("2026-10-11"). */
    fun playDaily(key: String) {
        val g = game
        if (g != null && g.daily == key && !g.won) return go(Screen.GAME)
        if (preparing != null) return
        preparing = key
        viewModelScope.launch {
            val deal = withContext(Dispatchers.Default) { Daily.deal(key).game }
            preparing = null
            start(deal)
        }
    }

    /** Leaving a game after at least one move counts as a loss, like the original app. */
    private fun recordAbandon() {
        val g = game ?: return
        if (!g.started || g.won) return
        val t = g.tick(clockNow().takeIf { runningSince != null } ?: g.elapsedMs)
        saveStats(stats.recordLoss(t.score, t.seconds, t.moves, System.currentTimeMillis(), t.daily != null))
    }

    // ------------------------------------------------------------ moves

    /** Tap on a card: send it (and anything on top of it) to its best place. */
    fun tap(loc: Loc, index: Int): Boolean {
        val g = game ?: return false
        if (busy) return false
        val m = g.autoMove(loc, index)
        if (m == null) {
            val pile = g.pile(loc)
            if (loc.type != PileType.STOCK && index in pile.indices) shake = Shake(pile.drop(index), ++shakeId)
            return false
        }
        return doMove(m)
    }

    /** A drag released over [to]. */
    fun drop(from: Loc, count: Int, to: Loc): Boolean {
        if (busy) return false
        return doMove(Move.Transfer(from, to, count))
    }

    fun isLegalDrop(from: Loc, count: Int, to: Loc): Boolean = game?.isLegal(Move.Transfer(from, to, count)) == true

    private val busy get() = autoCompleting || celebrating || game?.won == true

    private fun doMove(m: Move): Boolean {
        val g0 = game ?: return false
        val g = if (runningSince != null) g0.tick(clockNow()) else g0
        val n = g.apply(m) ?: return false
        val flipped = n.history.lastOrNull()?.flipped == true
        game = n
        hint = null; noMoves = false
        if (settings.sounds) sounds.play(if (m == Move.Draw || flipped) Sounds.Fx.FLIP else Sounds.Fx.PLACE)
        if (n.won) onWin(n)
        saveGame()
        syncClock()
        clockSec = n.seconds.coerceAtLeast(clockSec)
        return true
    }

    fun undo() {
        val g = game ?: return
        if (busy) return
        val t = if (runningSince != null) g.tick(clockNow()) else g
        val u = t.undo() ?: return
        game = u
        hint = null; noMoves = false
        if (settings.sounds) sounds.play(Sounds.Fx.PLACE)
        saveGame()
    }

    fun requestHint() {
        val g = game ?: return
        if (busy) return
        val h = g.hint()
        if (h == null && !g.canAutoComplete) { noMoves = true; hint = null } else { hint = h; noMoves = false }
    }

    fun dismissNoMoves() { noMoves = false }

    fun autoComplete() {
        if (autoCompleting || game?.canAutoComplete != true) return
        autoCompleting = true
        hint = null
        viewModelScope.launch {
            while (true) {
                val g = game ?: break
                val m = g.autoCompleteMove() ?: break
                autoCompleting = false
                doMove(m)
                autoCompleting = true
                if (game?.won == true) break
                delay(if (settings.animations) 120 else 30)
            }
            autoCompleting = false
        }
    }

    private fun onWin(g: Klondike) {
        val final = g.finalScore
        winInfo = WinInfo(stats.rankOf(final), final > stats.bestScore)
        saveStats(stats.recordWin(final, g.seconds, g.moves, LocalDate.now().toString(), System.currentTimeMillis(), g.daily != null))
        g.daily?.let { daily = daily.with(it); store.save(viewModelScope, K_DAILY, DailyProgress.serializer(), daily) }
        celebrating = settings.winAnimation && settings.animations
        if (settings.sounds) sounds.play(Sounds.Fx.WIN)
    }

    fun celebrationDone() { celebrating = false }

    // ------------------------------------------------------------ settings & stats

    fun updateSettings(s: AppSettings) {
        if (s.sounds && !settings.sounds) sounds.warmUp()
        settings = s
        store.save(viewModelScope, K_SETTINGS, AppSettings.serializer(), s)
    }

    private fun saveStats(s: Stats) {
        stats = s
        store.save(viewModelScope, K_STATS, Stats.serializer(), s)
    }

    fun resetStats() = saveStats(Stats())

    private fun saveGame(g: Klondike? = game) {
        g ?: return
        store.save(viewModelScope, K_GAME, Klondike.serializer(), g)
    }

    override fun onCleared() { sounds.release() }

    companion object {
        const val K_SETTINGS = "settings"
        const val K_STATS = "stats"
        const val K_DAILY = "daily"
        const val K_GAME = "game"
        const val K_SCREEN = "screen"
    }
}
