package dadgames.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dadgames.cards.Card
import dadgames.cards.Rank
import dadgames.cards.Suit

/** Suit with the text-presentation selector so it never renders as an emoji. [BEST_PRACTICES #6] */
val Suit.glyph: String get() = symbol + "︎"

@Composable
fun suitColor(s: Suit): Color = if (s.red) P.red else P.ink

@Composable
private fun Dp.sp(): TextUnit = with(LocalDensity.current) { this@sp.toSp() }

/** Card height for a given width (poker proportions). */
fun cardHeight(width: Dp): Dp = width * 1.42f

/**
 * A playing card. The **big index** (rank + suit, large, in the top strip) is readable even when
 * the card is mostly covered in a stack, like dad's Solitaire app. [research]
 */
@Composable
fun PlayingCard(
    card: Card,
    width: Dp,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    glow: Boolean = false,
) {
    val shape = RoundedCornerShape(width * 0.09f)
    val ink = suitColor(card.suit)
    val glowColor = P.accent
    Box(
        modifier
            .size(width, cardHeight(width))
            .shadow(if (glow) 10.dp else 2.dp, shape, ambientColor = if (glow) glowColor else Color.Black, spotColor = if (glow) glowColor else Color.Black)
            .clip(shape)
            .background(P.cardFace)
            .border(if (glow) 3.dp else 0.75.dp, if (glow) glowColor else P.cardEdge, shape)
    ) {
        // Big index across the top strip.
        Row(Modifier.align(Alignment.TopStart).padding(start = width * 0.06f, top = width * 0.02f), verticalAlignment = Alignment.CenterVertically) {
            Text(card.rank.short, color = ink, fontSize = (width * 0.36f).sp(), fontWeight = FontWeight.Bold, style = centeredGlyph)
            Text(card.suit.glyph, color = ink, fontSize = (width * 0.3f).sp(), style = centeredGlyph, modifier = Modifier.padding(start = width * 0.03f))
        }
        // Centre art.
        val face = card.rank.isFace
        Box(Modifier.align(Alignment.Center).padding(top = width * 0.28f)) {
            if (face) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(card.rank.short, color = ink, fontSize = (width * 0.42f).sp(), fontFamily = Serif, fontWeight = FontWeight.Bold, style = centeredGlyph)
                    Text(card.suit.glyph, color = ink, fontSize = (width * 0.24f).sp(), style = centeredGlyph)
                }
            } else {
                Text(card.suit.glyph, color = ink, fontSize = (width * if (card.rank == Rank.ACE) 0.62f else 0.5f).sp(), style = centeredGlyph)
            }
        }
        // Small mirrored index bottom-right.
        Column(Modifier.align(Alignment.BottomEnd).padding(end = width * 0.06f, bottom = width * 0.04f).rotate(180f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(card.rank.short, color = ink, fontSize = (width * 0.2f).sp(), fontWeight = FontWeight.Bold, style = centeredGlyph)
        }
        if (dim) Box(Modifier.fillMaxSize().background(Color(0x66000000)))
    }
}

@Composable
fun CardBack(width: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(width * 0.09f)
    val p = P
    Box(
        modifier
            .size(width, cardHeight(width))
            .shadow(2.dp, shape)
            .clip(shape)
            .background(p.cardFace)
            .padding(width * 0.06f)
            .clip(RoundedCornerShape(width * 0.06f))
            .background(Brush.linearGradient(listOf(p.backA, p.backB)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = size.width / 4f
            val line = Color(0x40EBC66E)
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(line, Offset(x, 0f), Offset(x + size.height, size.height), 1.2f)
                drawLine(line, Offset(x + size.height, 0f), Offset(x, size.height), 1.2f)
                x += step
            }
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.width * 0.22f
            val path = Path().apply {
                moveTo(c.x, c.y - r * 1.3f); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r * 1.3f); lineTo(c.x - r, c.y); close()
            }
            drawPath(path, p.backB)
            drawPath(path, Color(0xFFEBC66E), style = Stroke(2f))
            drawRect(Color(0x55EBC66E), style = Stroke(2f))
        }
    }
}

/** Empty pile outline (foundation / empty column) with an optional faint label like "A". */
@Composable
fun EmptySlot(width: Dp, label: String? = null, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(width * 0.09f)
    Box(
        modifier.size(width, cardHeight(width)).clip(shape).background(Color(0x22000000)).border(1.5.dp, P.text.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) Text(label, color = P.text.copy(alpha = 0.45f), fontSize = (width * 0.5f).sp(), fontFamily = Serif, style = centeredGlyph)
    }
}
