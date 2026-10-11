package dadgames.blackjack

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
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
 * Fan step (fraction of card width) that keeps a covered card's whole corner index visible:
 * the index is rank over suit, and the widest rank ("10") plus margin is under half a card.
 */
const val INDEX_STEP = 0.5f

/**
 * Playing card for the table. Unlike the shared PlayingCard, the corner index is **rank above suit**
 * (narrow, so a half-card fan shows it in full) and the small bottom-right index is upright with
 * its suit (a rotated 6 reads as 9). [BEST_PRACTICES 12k]
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
        Column(
            Modifier.align(Alignment.TopStart).padding(start = width * 0.05f, top = width * 0.05f).width(width * (INDEX_STEP - 0.06f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val ten = card.rank == Rank.TEN
            Text(
                card.rank.short, color = ink, fontSize = (width * if (ten) 0.30f else 0.36f).sp(), fontWeight = FontWeight.Bold,
                style = centeredGlyph, maxLines = 1, softWrap = false, letterSpacing = if (ten) (-1).sp else 0.sp,
            )
            Text(card.suit.glyph, color = ink, fontSize = (width * 0.30f).sp(), style = centeredGlyph, modifier = Modifier.padding(top = width * 0.04f))
        }
        // Centre art sits right of and below the index.
        Box(Modifier.align(Alignment.Center).padding(start = width * 0.42f, top = width * 0.42f)) {
            if (card.rank.isFace) {
                Text(card.rank.short, color = ink, fontSize = (width * 0.46f).sp(), fontFamily = Serif, fontWeight = FontWeight.Bold, style = centeredGlyph)
            } else {
                Text(card.suit.glyph, color = ink, fontSize = (width * if (card.rank == Rank.ACE) 0.56f else 0.46f).sp(), style = centeredGlyph)
            }
        }
    }
}
