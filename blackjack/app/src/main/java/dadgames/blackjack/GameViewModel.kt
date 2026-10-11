package dadgames.blackjack

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dadgames.blackjack.core.Action
import dadgames.blackjack.core.CHIPS
import dadgames.blackjack.core.Game
import dadgames.blackjack.core.Outcome
import dadgames.blackjack.core.Phase
import dadgames.blackjack.core.Rules
import dadgames.blackjack.core.Strategy
import dadgames.blackjack.core.TABLES
import dadgames.blackjack.core.TableInfo
import dadgames.blackjack.core.addChip
import dadgames.blackjack.core.maxBetFor
import dadgames.blackjack.core.points
import dadgames.ui.Store
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.security.SecureRandom

/** Dad's numbers from his old app (research/README.md). */
const val SEED_BALANCE = 559_864_578L
const val SEED_LEVEL = 868
const val SEED_BET = 250_000L
/** Free top-up when he can't cover the smallest chip. Never sold. */
const val REFILL = 10_000_000L

/** XP to go from [level] to the next: 50 + level (918 XP at level 868, about 75 hands). */
fun xpToNext(level: Int): Int = 50 + level

/** XP for one hand: 10 for playing, +5 for a winning hand, +10 more for a blackjack. */
fun xpFor(net: Long, blackjack: Boolean): Int = 10 + (if (net > 0) 5 else 0) + (if (blackjack) 10 else 0)

@Serializable
data class Settings(
    val sound: Boolean = false,
    val hintButton: Boolean = true,
    val autoHint: Boolean = false,
    /** 0 relaxed, 1 normal, 2 quick. */
    val speed: Int = 1,
    val rules: Rules = Rules(),
)

@Serializable
data class Profile(
    val level: Int = SEED_LEVEL,
    val xp: Int = 0,
    val table: Int = 0,
    /** Bet on the felt between hands (kept from the last hand). */
    val bet: Long = SEED_BET,
    val refills: Int = 0,
)

@Serializable
data class HandRecord(
    val time: Long,
    val table: String,
    val staked: Long,
    val net: Long,
    val result: String,
    val player: String,
    val dealer: String,
)

@Serializable
data class Stats(
    val hands: Long = 0,
    val won: Long = 0,
    val lost: Long = 0,
    val pushed: Long = 0,
    val blackjacks: Long = 0,
    val doubles: Long = 0,
    val doublesWon: Long = 0,
    val splits: Long = 0,
    val surrenders: Long = 0,
    val biggestWin: Long = 0,
    val biggestBet: Long = 0,
    val bestBalance: Long = SEED_BALANCE,
    /** Positive: wins in a row. Negative: losses in a row. Pushes don't break it. */
    val streak: Int = 0,
    val bestStreak: Int = 0,
    /** Last [Game.rounds] counted, so a resumed round is never counted twice. */
    val lastRound: Long = 0,
    /** Newest first, capped at 30. */
    val recent: List<HandRecord> = emptyList(),
    /** Balance after each of the last 200 hands, oldest first. */
    val history: List<Long> = listOf(SEED_BALANCE),
) {
    val winRate: Int get() = if (hands == 0L) 0 else Math.round(won * 100.0 / hands).toInt()
}

enum class Screen { HOME, TABLE, TABLES, SETTINGS, STATS, HELP }

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app, "blackjack")
    private val sound = Sound()
    private val rng = SecureRandom()

    /** Screen shown; saved so a relaunch puts him back where he was (at the table, mid-hand). [BEST_PRACTICES 12j] */
    var screen: Screen
        get() = screenState
        set(v) { screenState = v; store.putBoolean("atTable", v == Screen.TABLE) }
    private var screenState by mutableStateOf(Screen.HOME)
    var settings by mutableStateOf(store.load("settings", Settings.serializer()) ?: Settings())
        private set
    var stats by mutableStateOf(store.load("stats", Stats.serializer()) ?: Stats())
        private set
    var profile by mutableStateOf(store.load("profile", Profile.serializer()) ?: Profile())
        private set
    var game by mutableStateOf(store.load("game", Game.serializer()) ?: Game.new(settings.rules, SEED_BALANCE, rng.nextLong()))
        private set

    /** Cards with an id at or above this fly in from the shoe; older ones are already on the table. */
    var batchStart by mutableIntStateOf(game.nextId)
        private set
    /** Animations running: buttons wait. */
    var busy by mutableStateOf(false)
        private set
    var sweeping by mutableStateOf(false)
        private set
    var shuffling by mutableStateOf(false)
        private set
    /** After a round: 0 chips on the table, 1 winnings arrive / lost chips go to the dealer, 2 chips go to the balance. */
    var chipStage by mutableIntStateOf(0)
        private set
    var showHint by mutableStateOf(false)
        private set
    var autoDeal by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    /** The opening four cards are in the air: the buttons wait until they land. */
    var dealing by mutableStateOf(false)
        private set

    private var job: Job? = null
    private val skip = Channel<Unit>(Channel.CONFLATED)
    private var strategy: Strategy? = null

    val table: TableInfo get() = TABLES[profile.table.coerceIn(TABLES.indices)]
    val bet: Long get() = profile.bet

    /** Rules shown on the felt: the round's own while playing, the next hand's between hands. */
    val feltRules: Rules get() = if (game.phase == Phase.BETTING) settings.rules.normalized() else game.rules

    /** Balance as shown: chips on the felt are already off it; winnings land when the chips do. */
    val shownBalance: Long
        get() {
            val g = game
            return when (g.phase) {
                Phase.BETTING -> g.balance - bet
                Phase.ROUND_OVER -> if (chipStage < 2) g.balance - (g.summary?.returned ?: 0) else g.balance
                else -> g.balance
            }
        }

    val midHand: Boolean get() = game.phase != Phase.BETTING
    val broke: Boolean get() = game.phase == Phase.BETTING && game.balance < CHIPS.first()
    val canAct: Boolean get() = !busy && (game.phase == Phase.PLAYER || game.phase == Phase.INSURANCE)

    /** Basic-strategy move for the current rules and hand. */
    val hintAction: Action?
        get() {
            val g = game
            if (!canAct) return null
            val s = strategy?.takeIf { it.rules == g.rules } ?: Strategy(g.rules).also { strategy = it }
            return s.best(g)
        }

    init {
        // Back to the table if that's where he was (always when a hand was in progress).
        if (store.getBoolean("atTable", false) || game.phase != Phase.BETTING) openTable()
    }

    // ---------------------------------------------------------------- timings

    private val sp get() = settings.speed.coerceIn(0, 2)
    val stagger: Long get() = longArrayOf(320, 220, 130)[sp]
    val flight: Long get() = longArrayOf(460, 340, 230)[sp]
    private val dealerPause get() = longArrayOf(850, 600, 360)[sp]
    private val resultHold get() = longArrayOf(1900, 1300, 800)[sp]

    // ---------------------------------------------------------------- navigation

    fun openTable() {
        batchStart = game.nextId
        screen = Screen.TABLE
        if (job?.isActive != true) job = viewModelScope.launch { flow() }
    }

    fun leaveTable() {
        autoDeal = false
        screen = Screen.HOME
    }

    fun selectTable(i: Int) {
        if (midHand) return
        val t = TABLES[i]
        val b = bet.coerceAtMost(maxBetFor(t, game.balance)).coerceAtLeast(minOf(t.minBet, game.balance))
        saveProfile(profile.copy(table = i, bet = b))
        openTable()
    }

    // ---------------------------------------------------------------- betting

    fun tapChip(v: Long): Boolean {
        if (game.phase != Phase.BETTING || busy) return false
        val nb = addChip(bet, v, table, game.balance)
        if (nb == bet) {
            flash(if (bet >= table.maxBet) "Table limit is ${table.maxBet.short()}" else "That's all your chips")
            return false
        }
        saveProfile(profile.copy(bet = nb))
        play(Sound.Fx.CHIP)
        return true
    }

    fun clearBet() {
        if (game.phase != Phase.BETTING || busy) return
        autoDeal = false
        saveProfile(profile.copy(bet = 0))
    }

    fun refill() {
        if (!broke) return
        commit(game.copy().also { it.balance += REFILL })
        saveProfile(profile.copy(refills = profile.refills + 1, bet = minOf(SEED_BET, REFILL)))
    }

    /** Hold Deal: deal now and keep dealing the same bet after every hand until he taps to stop. */
    fun startAutoDeal() {
        if (deal()) autoDeal = true
    }

    fun stopAutoDeal() { autoDeal = false }

    fun deal(): Boolean {
        val g0 = game
        if (g0.phase != Phase.BETTING || busy) return false
        val t = table
        when {
            bet <= 0 -> { flash("Tap the chips to make a bet"); return false }
            bet < t.minBet -> { flash("Minimum bet at ${t.name} is ${t.minBet.short()}"); return false }
            bet > g0.balance -> { flash("Not enough chips for that bet"); return false }
            bet > t.maxBet -> { flash("Table limit is ${t.maxBet.short()}"); return false }
        }
        busy = true
        showHint = false
        job = viewModelScope.launch {
            val g = g0.copy()
            g.rules = settings.rules.normalized()
            g.seed = rng.nextLong()
            if (g.needsShuffle && g.discards.isNotEmpty()) {
                shuffling = true
                play(Sound.Fx.SHUFFLE)
                delay(1100)
                shuffling = false
            }
            batchStart = g.nextId
            dealing = true
            g.deal(bet)
            commit(g)
            repeat(4) { i -> viewModelScope.launch { delay(i * stagger); play(Sound.Fx.CARD) } }
            delay(3 * stagger + flight)
            dealing = false
            flow()
        }
        return true
    }

    // ---------------------------------------------------------------- play

    fun act(a: Action) {
        if (!canAct || a !in game.legal()) return
        busy = true
        showHint = false
        job = viewModelScope.launch {
            val g = game.copy()
            val before = g.nextId
            batchStart = before
            g.act(a)
            commit(g)
            val dealt = g.nextId - before
            if (a == Action.DOUBLE || a == Action.SPLIT || a == Action.INSURANCE) play(Sound.Fx.CHIP)
            repeat(dealt) { i -> viewModelScope.launch { delay(i * stagger); play(Sound.Fx.CARD) } }
            if (dealt > 0) delay((dealt - 1) * stagger + flight) else delay(120)
            flow()
        }
    }

    fun toggleHint() { if (canAct) showHint = !showHint }

    /** Tap on the felt while a result is showing: move on now. */
    fun tapFelt() { skip.trySend(Unit) }

    /** Runs the parts nobody decides: dealer's turn, settling, sweeping, auto-deal. */
    private suspend fun flow() {
        while (true) {
            val g = game
            when (g.phase) {
                Phase.PLAYER, Phase.INSURANCE, Phase.BETTING -> { busy = false; return }
                Phase.DEALER -> {
                    busy = true
                    delay(dealerPause)
                    val n = g.copy()
                    batchStart = n.nextId
                    val flip = n.holeHidden
                    n.dealerStep()
                    commit(n)
                    if (n.dealer.size > g.dealer.size) play(Sound.Fx.CARD)
                    if (flip || n.dealer.size > g.dealer.size) delay(flight)
                }
                Phase.ROUND_OVER -> { roundEnd(); return }
            }
        }
    }

    private suspend fun roundEnd() {
        busy = true
        while (skip.tryReceive().isSuccess) { /* drop stale taps */ }
        chipStage = 0
        delay(450)
        chipStage = 1
        val net = game.summary?.net ?: 0
        play(if (net > 0) Sound.Fx.WIN else if (net < 0) Sound.Fx.LOSE else Sound.Fx.CHIP)
        delay(650)
        chipStage = 2
        play(Sound.Fx.CHIP)
        withTimeoutOrNull(resultHold) { skip.receive() }
        sweeping = true
        delay(flight + SWEEP_STEP * (game.tableCards.size + 2))
        val n = game.copy()
        n.collect()
        // Keep the same bet for the next hand if he can still cover it.
        val nb = bet.coerceAtMost(maxBetFor(table, n.balance))
        commit(n)
        if (nb != bet) saveProfile(profile.copy(bet = nb))
        sweeping = false
        chipStage = 0
        busy = false
        if (autoDeal) {
            if (bet >= table.minBet && bet <= n.balance && screen == Screen.TABLE) {
                delay(250)
                deal()
            } else autoDeal = false
        }
    }

    // ---------------------------------------------------------------- saving and stats

    private fun commit(g: Game) {
        if (g.phase == Phase.ROUND_OVER && g.rounds > stats.lastRound) record(g)
        game = g
        store.save(viewModelScope, "game", Game.serializer(), g)
    }

    private fun record(g: Game) {
        val s = g.summary ?: return
        val net = s.net
        val bj = g.hands.any { it.outcome == Outcome.BLACKJACK }
        val win = net > 0
        val loss = net < 0
        val streak = when {
            win -> if (stats.streak > 0) stats.streak + 1 else 1
            loss -> if (stats.streak < 0) stats.streak - 1 else -1
            else -> stats.streak
        }
        val result = when {
            g.hands.size > 1 -> "Split: " + g.hands.joinToString(", ") { it.outcome?.label ?: "" }
            s.insuranceWon && g.hands[0].outcome == Outcome.PUSH -> "Even money"
            else -> g.hands[0].outcome?.label ?: ""
        }
        val dealer = when {
            s.dealerBlackjack -> "Blackjack"
            s.dealerBust -> "Bust"
            g.hands.all { it.totals.bust || it.surrendered } -> "-"
            else -> "${s.dealerTotal}"
        }
        val rec = HandRecord(
            System.currentTimeMillis(), table.name, s.staked, net, result,
            g.hands.joinToString(", ") { if (it.natural) "BJ" else "${it.totals.best}" }, dealer,
        )
        stats = stats.copy(
            hands = stats.hands + 1,
            won = stats.won + if (win) 1 else 0,
            lost = stats.lost + if (loss) 1 else 0,
            pushed = stats.pushed + if (!win && !loss) 1 else 0,
            blackjacks = stats.blackjacks + if (bj) 1 else 0,
            doubles = stats.doubles + g.hands.count { it.doubled },
            doublesWon = stats.doublesWon + g.hands.count { it.doubled && it.outcome == Outcome.WIN },
            splits = stats.splits + (g.hands.size - 1),
            surrenders = stats.surrenders + g.hands.count { it.surrendered },
            biggestWin = maxOf(stats.biggestWin, net),
            biggestBet = maxOf(stats.biggestBet, s.staked),
            bestBalance = maxOf(stats.bestBalance, g.balance),
            streak = streak,
            bestStreak = maxOf(stats.bestStreak, streak),
            lastRound = g.rounds,
            recent = (listOf(rec) + stats.recent).take(30),
            history = (stats.history + g.balance).takeLast(200),
        )
        store.save(viewModelScope, "stats", Stats.serializer(), stats)
        var lv = profile.level
        var xp = profile.xp + xpFor(net, bj)
        while (xp >= xpToNext(lv)) { xp -= xpToNext(lv); lv++ }
        saveProfile(profile.copy(level = lv, xp = xp))
    }

    fun resetStats() {
        stats = Stats(bestBalance = game.balance, history = listOf(game.balance), lastRound = game.rounds)
        store.save(viewModelScope, "stats", Stats.serializer(), stats)
    }

    fun updateSettings(s: Settings) {
        settings = s
        store.save(viewModelScope, "settings", Settings.serializer(), s)
    }

    private fun saveProfile(p: Profile) {
        profile = p
        store.save(viewModelScope, "profile", Profile.serializer(), p)
    }

    private var flashJob: Job? = null
    private fun flash(m: String) {
        message = m
        flashJob?.cancel()
        flashJob = viewModelScope.launch { delay(2200); message = null }
    }

    /** Debug builds only (see MainActivity): put these card values on top of the shoe for screenshots. */
    fun debugStack(values: List<Int>) {
        if (game.phase != Phase.BETTING) return
        val g = game.copy()
        if (g.needsShuffle) g.reshuffle()
        val top = values.map { v ->
            val i = g.shoe.indexOfFirst { c -> c.points == v }
            g.shoe.removeAt(i)
        }
        g.shoe.addAll(top.reversed())
        commit(g)
    }

    /** Debug builds only: set the balance (to show the out-of-chips panel). */
    fun debugBalance(b: Long) {
        if (game.phase != Phase.BETTING) return
        commit(game.copy().also { it.balance = b })
        saveProfile(profile.copy(bet = minOf(bet, b)))
    }

    private fun play(fx: Sound.Fx) { if (settings.sound) sound.play(fx) }
}
