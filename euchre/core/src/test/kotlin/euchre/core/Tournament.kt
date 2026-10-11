package euchre.core

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Run with -Ptournament: ./gradlew :core:test --tests '*Tournament*' -Ptournament */
class Tournament {
    private val games = (System.getProperty("games") ?: "300").toInt()

    /** Win rate of team A (seats 0, 2) against team B. */
    private fun match(a: BotProfile, b: BotProfile, rules: Rules = Rules()): Double {
        val wins = AtomicInteger()
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        (0 until games).map { g ->
            pool.submit {
                val bots = List(4) { Bot(if (it % 2 == 0) a else b, g * 31L + it) }
                val s = GameState.new(rules, 1000L + g)
                while (s.phase != Phase.GAME_OVER) s.apply(bots[s.turn].choose(s, s.turn))
                if (s.winner == 0) wins.incrementAndGet()
            }
        }.forEach { it.get() }
        pool.shutdown()
        return wins.get().toDouble() / games
    }

    @Test fun ladder() {
        assumeTrue(System.getProperty("tournament") != null)
        val base = BotProfile(50, 50)
        println("IQ vs base(aggr 50, iq 50), $games games each")
        for (iq in listOf(0, 20, 40, 60, 70, 85, 100)) {
            val t0 = System.nanoTime()
            val w = match(BotProfile(50, iq), base)
            println("  iq %3d: %.3f   (%.1fs)".format(iq, w, (System.nanoTime() - t0) / 1e9))
        }
        println("Aggression vs base, iq 50")
        for (ag in listOf(0, 25, 50, 75, 100)) println("  aggr %3d: %.3f".format(ag, match(BotProfile(ag, 50), base)))
        println("Aggression vs iq-90 aggr-50, iq 90")
        for (ag in listOf(0, 25, 50, 75, 100)) println("  aggr %3d: %.3f".format(ag, match(BotProfile(ag, 90), BotProfile(50, 90))))
    }
}
