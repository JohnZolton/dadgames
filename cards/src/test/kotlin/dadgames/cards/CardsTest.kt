package dadgames.cards

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class CardsTest {
    @Test fun deckHas52UniqueCards() {
        assertEquals(52, standardDeck().toSet().size)
        assertEquals(8 * 52, shuffledShoe(8, Random(1)).size)
        assertEquals("Queen of Hearts", Card(Rank.QUEEN, Suit.HEARTS).spoken)
    }
}
