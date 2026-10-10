package euchre.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import euchre.core.Card
import euchre.core.Rank
import euchre.core.Suit

/** Unicode suit with the text-presentation selector so it never turns into an emoji. */
val Suit.glyph: String get() = symbol + "︎"
val Suit.color: Color get() = if (red) Palette.red else Palette.ink

@Composable
private fun Dp.sp(): TextUnit = with(LocalDensity.current) { this@sp.toSp() }

@Composable
fun PlayingCard(
    card: Card,
    width: Dp,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    glow: Boolean = false,
) {
    val shape = RoundedCornerShape(width * 0.11f)
    val ink = if (card.isJoker) Palette.ink else card.suit.color
    Box(
        modifier
            .size(width, width * 1.42f)
            .shadow(if (glow) 10.dp else 3.dp, shape, ambientColor = if (glow) Palette.gold else Color.Black, spotColor = if (glow) Palette.gold else Color.Black)
            .clip(shape)
            .background(Palette.cream)
            .border(if (glow) 3.dp else 0.75.dp, if (glow) Palette.gold else Palette.cardEdge, shape)
    ) {
        if (card.isJoker) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("★", color = Palette.red, fontSize = (width * 0.5f).sp())
                Text("JOKER", color = Palette.ink, fontSize = (width * 0.16f).sp(), fontWeight = FontWeight.Bold)
            }
        } else {
            Corner(card, width, ink, Modifier.align(Alignment.TopStart))
            Corner(card, width, ink, Modifier.align(Alignment.BottomEnd).rotate(180f))
            val face = card.rank == Rank.JACK || card.rank == Rank.QUEEN || card.rank == Rank.KING
            if (face) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(card.rank.short, color = ink, fontSize = (width * 0.46f).sp(), fontFamily = Serif, fontWeight = FontWeight.Bold)
                    Text(card.suit.glyph, color = ink, fontSize = (width * 0.26f).sp())
                }
            } else {
                Text(
                    card.suit.glyph, color = ink,
                    fontSize = (width * if (card.rank == Rank.ACE) 0.62f else 0.46f).sp(),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        if (dim) Box(Modifier.fillMaxSize().background(Color(0x66000000)))
    }
}

@Composable
private fun Corner(card: Card, width: Dp, ink: Color, modifier: Modifier) {
    Column(modifier.padding(start = width * 0.07f, top = width * 0.04f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(card.rank.short, color = ink, fontSize = (width * 0.25f).sp(), fontWeight = FontWeight.Bold, lineHeight = (width * 0.26f).sp())
        Text(card.suit.glyph, color = ink, fontSize = (width * 0.21f).sp(), lineHeight = (width * 0.22f).sp())
    }
}

@Composable
fun CardBack(width: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(width * 0.11f)
    Box(
        modifier
            .size(width, width * 1.42f)
            .shadow(2.dp, shape)
            .clip(shape)
            .background(Palette.cream)
            .padding(width * 0.06f)
            .clip(RoundedCornerShape(width * 0.07f))
            .background(Brush.linearGradient(listOf(Palette.backA, Palette.backB)))
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
            val p = Path().apply {
                moveTo(c.x, c.y - r * 1.3f); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r * 1.3f); lineTo(c.x - r, c.y); close()
            }
            drawPath(p, Palette.backB)
            drawPath(p, Palette.gold, style = Stroke(2f))
            drawRect(Palette.goldSoft, style = Stroke(2f))
        }
    }
}
