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
    /** Your side euchred them. */
    val euchres: Int = 0,
    /** Lone marches (all five tricks alone) by you. */
    val loners: Int = 0,
    /** Your side called trump and got euchred. */
    val euchredUs: Int = 0,
    val marches: Int = 0,
    val lonersTried: Int = 0,
    val lonersMade: Int = 0,
    val pointsFor: Int = 0,
    val pointsAgainst: Int = 0,
    /** Newest first, capped at 20. */
    val recent: List<GameRecord> = emptyList(),
) {
    val winRate: Int get() = if (played == 0) 0 else Math.round(won * 100f / played)
}

@Serializable
data class GameRecord(val time: Long, val won: Boolean, val us: Int, val them: Int, val players: Int, val abandoned: Boolean = false)

/** Where a card was on screen when you played it, so it can glide from there to the table. */
data class HandLaunch(val card: euchre.core.Card, val center: androidx.compose.ui.geometry.Offset, val tilt: Float, val widthDp: Float)

/** A finished trick held on the table for a moment before it's swept to the winner. */
data class ShownTrick(val cards: List<Played>, val winner: Int, val sweeping: Boolean = false)

enum class Screen { HOME, GAME, SETTINGS, HELP, STATS }

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

    /** UI geometry for the hand → table handoff (not game state, so not persisted). */
    var handLaunch: HandLaunch? = null
    var tableCenter = androidx.compose.ui.geometry.Offset.Zero

    fun takeLaunch(card: euchre.core.Card): HandLaunch? =
        handLaunch?.takeIf { it.card == card }.also { if (it != null) handLaunch = null }

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

    /** One writer thread so saves land in order; snapshots are never mutated after this point. */
    private val saveDispatcher = Dispatchers.Default.limitedParallelism(1)

    private fun storeGame(g: GameState) {
        game = g
        viewModelScope.launch(saveDispatcher) { prefs.edit().putString("game", g.toJson()).apply() }
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
            euchredUs = stats.euchredUs + if (weCalled && !made) 1 else 0,
            marches = stats.marches + if (weCalled && r.makerTricks == 5) 1 else 0,
            lonersTried = stats.lonersTried + if (r.maker == 0 && r.alone) 1 else 0,
            lonersMade = stats.lonersMade + if (r.maker == 0 && r.alone && made) 1 else 0,
            pointsFor = stats.pointsFor + r.points[us],
            pointsAgainst = stats.pointsAgainst + r.points.withIndex().filter { it.index != us }.sumOf { it.value },
        )
        if (g.phase == Phase.GAME_OVER) {
            val won = g.winner == us
            val streak = if (won) s.streak + 1 else 0
            val rec = GameRecord(System.currentTimeMillis(), won, g.scores[us], themScore(g), g.players)
            s = s.copy(
                played = s.played + 1, won = s.won + if (won) 1 else 0, streak = streak,
                bestStreak = maxOf(s.bestStreak, streak), recent = (listOf(rec) + s.recent).take(20),
            )
        }
        saveStats(s)
    }

    private fun themScore(g: GameState): Int {
        val us = g.rules.team(0)
        return g.scores.withIndex().filter { it.index != us }.maxOf { it.value }
    }

    /** Leaving a game unfinished for a new one counts as a loss. */
    private fun recordAbandon() {
        val g = game ?: return
        // A fresh deal nobody has scored in yet isn't a game.
        if (g.scores.all { it == 0 } && g.result == null && g.tricks.isEmpty()) return
        val rec = GameRecord(System.currentTimeMillis(), false, g.scores[g.rules.team(0)], themScore(g), g.players, abandoned = true)
        saveStats(stats.copy(played = stats.played + 1, streak = 0, recent = (listOf(rec) + stats.recent).take(20)))
    }

    private fun saveStats(s: Stats) {
        stats = s
        prefs.edit().putString("stats", json.encodeToString(Stats.serializer(), s)).apply()
    }

    fun resetStats() = saveStats(Stats())
}
