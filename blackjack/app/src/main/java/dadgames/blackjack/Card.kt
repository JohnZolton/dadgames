package dadgames.blackjack

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dadgames.cards.Card
import dadgames.cards.Rank
import dadgames.ui.P
import dadgames.ui.Serif
import dadgames.ui.cardHeight
import dadgames.ui.centeredGlyph
import dadgames.ui.glyph
import dadgames.ui.suitColor

@Composable
private fun Dp.sp(): TextUnit = with(LocalDensity.current) { this@sp.toSp() }

/**
 * The shared PlayingCard, with one change: the small bottom-right index is upright and carries
 * the suit. The shared card turns a bare rank upside down, so a 6 there reads as a 9 (and 10 as
 * "01") right next to the total bubble.
 */
@Composable
fun BjCard(card: Card, width: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(width * 0.09f)
    val ink = suitColor(card.suit)
    Box(
        modifier
            .size(width, cardHeight(width))
            .shadow(2.dp, shape)
            .clip(shape)
            .background(P.cardFace)
            .border(0.75.dp, P.cardEdge, shape),
    ) {
        Row(Modifier.align(Alignment.TopStart).padding(start = width * 0.06f, top = width * 0.02f), verticalAlignment = Alignment.CenterVertically) {
            Text(card.rank.short, color = ink, fontSize = (width * 0.36f).sp(), fontWeight = FontWeight.Bold, style = centeredGlyph)
            Text(card.suit.glyph, color = ink, fontSize = (width * 0.3f).sp(), style = centeredGlyph, modifier = Modifier.padding(start = width * 0.03f))
        }
        Box(Modifier.align(Alignment.Center).padding(top = width * 0.28f)) {
            if (card.rank.isFace) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(card.rank.short, color = ink, fontSize = (width * 0.42f).sp(), fontFamily = Serif, fontWeight = FontWeight.Bold, style = centeredGlyph)
                    Text(card.suit.glyph, color = ink, fontSize = (width * 0.24f).sp(), style = centeredGlyph)
                }
            } else {
                Text(card.suit.glyph, color = ink, fontSize = (width * if (card.rank == Rank.ACE) 0.62f else 0.5f).sp(), style = centeredGlyph)
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(end = width * 0.06f, bottom = width * 0.04f), verticalAlignment = Alignment.CenterVertically) {
            Text(card.rank.short, color = ink, fontSize = (width * 0.18f).sp(), fontWeight = FontWeight.Bold, style = centeredGlyph)
            Text(card.suit.glyph, color = ink, fontSize = (width * 0.15f).sp(), style = centeredGlyph)
        }
    }
}
