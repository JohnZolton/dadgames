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
        // 2026-10-11 is the puzzle seen on the emulator: 8 gems, tray corner / L / T.
        "2026-10-11" to "8 4625439160986005504 2050 4620935110240256000 0 [11, 21, 27] -2356778434506692059",
        "2026-12-25" to "6 3585572576602098691 32 54044303634204672 0 [9, 48, 44] -2292210146606896798",
        "2027-02-28" to "7 36240179205279976 56 275951653024 40 [40, 3, 39] 893579751176971507",
    )

    @Test fun pinnedDates() {
        for ((date, expected) in pinned) {
            assertEquals(date, expected, fingerprint(date))
        }
    }
}
