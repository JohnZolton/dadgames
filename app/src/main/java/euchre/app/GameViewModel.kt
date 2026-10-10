package euchre.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import euchre.core.Action
import euchre.core.Bot
import euchre.core.BotProfile
import euchre.core.GameState
import euchre.core.Phase
import euchre.core.Played
import euchre.core.ResultKind
import euchre.core.Rules
import euchre.core.team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

@Serializable
data class AppSettings(
    val rules: Rules = Rules(),
    val partner: BotProfile = BotProfile(aggression = 50, iq = 65),
    val opponents: BotProfile = BotProfile(aggression = 50, iq = 55),
    /** 0 relaxed, 1 normal, 2 quick. */
    val speed: Int = 1,
    val autoPlaySingle: Boolean = true,
    val tapTwice: Boolean = false,
)

@Serializable
data class Stats(
    val played: Int = 0,
    val won: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val hands: Int = 0,
    val called: Int = 0,
    val made: Int = 0,
    val euchres: Int = 0,
    val loners: Int = 0,
)

/** A finished trick held on the table for a moment before it's swept to the winner. */
data class ShownTrick(val cards: List<Played>, val winner: Int, val sweeping: Boolean = false)

enum class Screen { HOME, GAME, SETTINGS, HELP }

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("euchre", Context.MODE_PRIVATE)

    var screen by mutableStateOf(Screen.HOME)
    var settings by mutableStateOf(load("settings") { AppSettings() })
        private set
    var stats by mutableStateOf(load("stats") { Stats() })
        private set
    var game by mutableStateOf(runCatching { prefs.getString("game", null)?.let { GameState.fromJson(it) } }.getOrNull())
        private set
    var shownTrick by mutableStateOf<ShownTrick?>(null)
        private set
    var hint by mutableStateOf<Action?>(null)
        private set
    var selected by mutableStateOf<euchre.core.Card?>(null)
    var thinking by mutableStateOf(false)
        private set

    private var bots = makeBots()
    private var job: Job? = null

    private inline fun <reified T> load(key: String, default: () -> T): T =
        runCatching { prefs.getString(key, null)?.let { json.decodeFromString<T>(it) } }.getOrNull() ?: default()

    val canResume: Boolean get() = game.let { it != null && it.phase != Phase.GAME_OVER }

    /** Human may act right now. */
    val humanTurn: Boolean
        get() = game.let { it != null && it.turn == 0 && shownTrick == null && it.phase != Phase.GAME_OVER && it.phase != Phase.HAND_OVER }

    private fun makeBots(): List<Bot> = List(4) { seat ->
        val profile = if (settings.rules.normalized().players == 4 && seat == 2) settings.partner else settings.opponents
        Bot(profile, Random.nextLong())
    }

    private fun botsFor(g: GameState): List<Bot> = List(g.players) { seat ->
        Bot(if (g.players == 4 && seat == 2) settings.partner else settings.opponents, Random.nextLong())
    }

    fun updateSettings(s: AppSettings) {
        settings = s
        prefs.edit().putString("settings", json.encodeToString(AppSettings.serializer(), s)).apply()
        game?.let { bots = botsFor(it) }
    }

    fun newGame() {
        job?.cancel()
        val g = GameState.new(settings.rules, Random.nextLong())
        if (canResume) recordAbandon()
        bots = botsFor(g)
        shownTrick = null
        hint = null
        selected = null
        storeGame(g)
        screen = Screen.GAME
        drive()
    }

    fun resume() {
        val g = game ?: return
        bots = botsFor(g)
        screen = Screen.GAME
        drive()
    }

    fun leaveGame() {
        job?.cancel()
        thinking = false
        screen = Screen.HOME
    }

    fun act(a: Action) {
        val g = game ?: return
        val allowed = when (g.phase) {
            Phase.HAND_OVER -> shownTrick == null
            else -> humanTurn
        }
        if (!allowed || a !in g.legalActions()) return
        apply(a)
        drive()
    }

    /** Tap on a card in your hand. */
    fun tapCard(card: euchre.core.Card) {
        val g = game ?: return
        if (!humanTurn) return
        val a = when (g.phase) {
            Phase.PLAY -> Action.Play(card)
            Phase.DISCARD, Phase.LONER_DISCARD -> Action.Discard(card)
            else -> return
        }
        if (a !in g.legalActions()) return
        if (settings.tapTwice && g.phase == Phase.PLAY && selected != card) {
            selected = card
            return
        }
        act(a)
    }

    /** Swipe up on a card: a deliberate gesture, so it skips the tap-twice confirmation. */
    fun swipeCard(card: euchre.core.Card) {
        val g = game ?: return
        if (!humanTurn) return
        val a = when (g.phase) {
            Phase.PLAY -> Action.Play(card)
            Phase.DISCARD, Phase.LONER_DISCARD -> Action.Discard(card)
            else -> return
        }
        act(a)
    }

    fun requestHint() {
        val g = game ?: return
        if (!humanTurn) return
        viewModelScope.launch {
            val copy = g.copy()
            hint = withContext(Dispatchers.Default) { Bot(BotProfile(50, 100)).choose(copy, 0) }
        }
    }

    private fun storeGame(g: GameState) {
        game = g
        prefs.edit().putString("game", g.toJson()).apply()
    }

    private fun apply(a: Action) {
        val g = game!!.copy()
        val tricksBefore = g.tricks.size
        g.apply(a)
        if (g.tricks.size > tricksBefore) shownTrick = ShownTrick(g.tricks.last(), g.trickWinners.last())
        if (g.result != null && game?.result == null) recordHand(g)
        hint = null
        selected = null
        storeGame(g)
    }

    private val botDelay get() = longArrayOf(1150, 750, 380)[settings.speed]
    private val trickHold get() = longArrayOf(1600, 1100, 650)[settings.speed]

    private fun drive() {
        job?.cancel()
        job = viewModelScope.launch {
            while (true) {
                shownTrick?.let { st ->
                    delay(trickHold)
                    shownTrick = st.copy(sweeping = true)
                    delay(380)
                    shownTrick = null
                }
                val g = game ?: return@launch
                if (g.phase == Phase.HAND_OVER || g.phase == Phase.GAME_OVER) return@launch
                if (g.turn == 0) {
                    val legal = g.legalActions()
                    if (settings.autoPlaySingle && g.phase == Phase.PLAY && legal.size == 1) {
                        delay(450)
                        apply(legal[0])
                        continue
                    }
                    return@launch
                }
                val started = System.currentTimeMillis()
                thinking = true
                val copy = g.copy()
                val bot = bots[g.turn]
                val a = withContext(Dispatchers.Default) { bot.choose(copy, copy.turn) }
                val wait = botDelay - (System.currentTimeMillis() - started)
                if (wait > 0) delay(wait)
                thinking = false
                apply(a)
            }
        }
    }

    private fun recordHand(g: GameState) {
        val r = g.result!!
        val us = g.rules.team(0)
        val weCalled = g.rules.team(r.maker) == us
        val made = r.makerTricks >= 3
        var s = stats.copy(
            hands = stats.hands + 1,
            called = stats.called + if (r.maker == 0) 1 else 0,
            made = stats.made + if (r.maker == 0 && made) 1 else 0,
            euchres = stats.euchres + if (!weCalled && !made) 1 else 0,
            loners = stats.loners + if (r.maker == 0 && r.alone && r.kind == ResultKind.LONE_MARCH) 1 else 0,
        )
        if (g.phase == Phase.GAME_OVER) {
            val won = g.winner == us
            val streak = if (won) s.streak + 1 else 0
            s = s.copy(played = s.played + 1, won = s.won + if (won) 1 else 0, streak = streak, bestStreak = maxOf(s.bestStreak, streak))
        }
        saveStats(s)
    }

    private fun recordAbandon() = saveStats(stats.copy(played = stats.played + 1, streak = 0))

    private fun saveStats(s: Stats) {
        stats = s
        prefs.edit().putString("stats", json.encodeToString(Stats.serializer(), s)).apply()
    }

    fun resetStats() = saveStats(Stats())
}
