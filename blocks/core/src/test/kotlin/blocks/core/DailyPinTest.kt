package blocks.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Past dailies must never change under him: these dates are pinned to their generated puzzles. [BEST_PRACTICES #12h] */
class DailyPinTest {
    private fun fingerprint(date: String): String {
        val g = Daily.generate(date)
        return "${g.gemsTotal} ${g.board.lo} ${g.board.hi} ${g.gems.lo} ${g.gems.hi} ${g.tray} ${g.rng.state}"
    }

    private val pinned = mapOf(
        // Re-pinned after switching to Woodoku's decompiled piece generator (same boards and gems, new trays).
        "2026-10-11" to "8 4625439160986005504 2050 4620935110240256000 0 [35, 43, 32] -7739465813405707613",
        "2026-12-25" to "6 3585572576602098691 32 54044303634204672 0 [28, 32, 17] 8080502858754131487",
        "2027-02-28" to "7 36240179205279976 56 275951653024 40 [25, 1, 36] -4489107627722044047",
    )

    @Test fun pinnedDates() {
        val actual = pinned.keys.associateWith { fingerprint(it) }
        for ((date, expected) in pinned) assertEquals(date, expected, actual[date])
    }
}
