package dadgames.blackjack.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** An unreadable game save must never cost him his chips or level. [BEST_PRACTICES 20a] */
class SavesTest {
    private val seed = 559_864_578L

    @Test fun bankKeyWinsOverSeed() {
        assertEquals(123_456L, SaveRecovery.startingBalance(Bank(123_456, 900), null, seed))
    }

    @Test fun salvagesBalanceFromDamagedSave() {
        // A save from some other (future) format: unknown fields, a missing one, broken types elsewhere.
        val raw = """{"rules":{"decks":"eight"},"balance":777000000,"phase":"BETTING","shoe":42,"newThing":{}}"""
        assertNull(runCatching { Game.fromJson(raw) }.getOrNull())
        assertEquals(777_000_000L, SaveRecovery.startingBalance(null, raw, seed))
    }

    @Test fun midHandBetIsRefundedWhenSaveCantBeRestored() {
        val g = Game.new(Rules(), 1_000_000, 5)
        g.deal(250_000)
        if (g.phase == Phase.INSURANCE) g.act(Action.NO_INSURANCE)
        val raw = g.toJson().replace("\"decks\":8", "\"decks\":\"broken\"")
        val expected = if (g.phase == Phase.ROUND_OVER) g.balance else 1_000_000L
        assertEquals(expected, SaveRecovery.balanceFromGame(raw))
        assertEquals(expected, SaveRecovery.bankBalance(g))
    }

    @Test fun garbageFallsBackToSeedOnlyWithNothingElse() {
        assertEquals(seed, SaveRecovery.startingBalance(null, "not json at all", seed))
        assertEquals(seed, SaveRecovery.startingBalance(null, null, seed))
    }

    @Test fun levelSalvage() {
        assertEquals(870 to 12, SaveRecovery.levelFromProfile("""{"level":870,"xp":12,"table":"x"}"""))
        assertNull(SaveRecovery.levelFromProfile("{}"))
    }

    @Test fun tableMinimumsAreChips() {
        for (t in TABLES) {
            assertEquals(t.name, t.minBet, chipsFor(t).first())
        }
    }
}
