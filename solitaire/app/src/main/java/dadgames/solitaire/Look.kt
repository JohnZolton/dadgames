package dadgames.solitaire

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dadgames.ui.GamePalette
import dadgames.ui.cardHeight
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    /** Off by default, like every game dad plays. [BEST_PRACTICES #3] */
    val sounds: Boolean = false,
    val animations: Boolean = true,
    val winAnimation: Boolean = true,
    /** 1 or 3 cards per draw. Dad plays Draw 1. */
    val draw: Int = 1,
    val background: Int = 0,
    val cardBack: Int = 0,
    /** Stock and waste on the left, foundations on the right. */
    val leftHanded: Boolean = false,
)

class Background(val name: String, val palette: GamePalette)

private val felt = GamePalette.Felt

/** Table colours. Green felt is dad's current choice. */
val backgrounds = listOf(
    Background("Green felt", felt),
    Background("Blue", felt.copy(bgLight = Color(0xFF2F6DA8), bg = Color(0xFF1F4F80), bgDark = Color(0xFF11304F), panel = Color(0xFF0D2236))),
    Background("Red", felt.copy(bgLight = Color(0xFFA33A3A), bg = Color(0xFF7E2626), bgDark = Color(0xFF4A1414), panel = Color(0xFF2E0D0D))),
    Background("Purple", felt.copy(bgLight = Color(0xFF6E4A9E), bg = Color(0xFF52347A), bgDark = Color(0xFF2F1C48), panel = Color(0xFF1F1230))),
    Background("Charcoal", felt.copy(bgLight = Color(0xFF4F5559), bg = Color(0xFF383D40), bgDark = Color(0xFF1F2224), panel = Color(0xFF151719))),
    Background("Teal", felt.copy(bgLight = Color(0xFF248A88), bg = Color(0xFF186A69), bgDark = Color(0xFF0C3F3F), panel = Color(0xFF082B2B))),
)

/** Card back designs. "Blue dots" matches dad's current app. */
class Back(val name: String, val base: Color, val ink: Color, val dots: Boolean)

val cardBacks = listOf(
    Back("Blue dots", Color(0xFF3B82D6), Color(0xFFE8F1FC), dots = true),
    Back("Red dots", Color(0xFFC63A3A), Color(0xFFFCE8E8), dots = true),
    Back("Green dots", Color(0xFF2E8B57), Color(0xFFE6F5EC), dots = true),
    Back("Navy", Color(0xFF243B6B), Color(0xFFEBC66E), dots = false),
    Back("Burgundy", Color(0xFF7A1F2B), Color(0xFFEBC66E), dots = false),
)

/** Face-down card in the chosen design. */
@Composable
fun CardBackArt(width: Dp, back: Back, modifier: Modifier = Modifier, glow: Boolean = false) {
    val shape = RoundedCornerShape(width * 0.09f)
    Box(
        modifier
            .size(width, cardHeight(width))
            .shadow(if (glow) 10.dp else 2.dp, shape, ambientColor = if (glow) GOLD else Color.Black, spotColor = if (glow) GOLD else Color.Black)
            .clip(shape)
            .background(Color(0xFFFFFCF4))
            .border(if (glow) 3.dp else 0.dp, if (glow) GOLD else Color.Transparent, shape)
            .padding(width * 0.05f)
            .clip(RoundedCornerShape(width * 0.06f))
            .background(back.base)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (back.dots) {
                // Polka dots on a diagonal grid, like the classic back dad uses.
                val step = size.width / 6.5f
                val r = step * 0.22f
                val dot = back.ink.copy(alpha = 0.85f)
                var row = 0
                var y = step * 0.4f
                while (y < size.height) {
                    var x = if (row % 2 == 0) step * 0.4f else step * 0.9f
                    while (x < size.width) { drawCircle(dot, r, Offset(x, y)); x += step }
                    y += step * 0.5f; row++
                }
            } else {
                val line = back.ink.copy(alpha = 0.3f)
                val step = size.width / 4f
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(line, Offset(x, 0f), Offset(x + size.height, size.height), 1.4f)
                    drawLine(line, Offset(x + size.height, 0f), Offset(x, size.height), 1.4f)
                    x += step
                }
                val c = Offset(size.width / 2, size.height / 2)
                val rr = size.width * 0.22f
                val p = Path().apply { moveTo(c.x, c.y - rr * 1.3f); lineTo(c.x + rr, c.y); lineTo(c.x, c.y + rr * 1.3f); lineTo(c.x - rr, c.y); close() }
                drawPath(p, back.base)
                drawPath(p, back.ink, style = Stroke(2f))
                drawRect(back.ink.copy(alpha = 0.5f), style = Stroke(2.5f))
            }
        }
    }
}

val GOLD = Color(0xFFEBC66E)

/** Table background: soft radial light, like a felt table under a lamp. */
fun tableBrush(p: GamePalette) = Brush.radialGradient(0f to p.bgLight, 0.6f to p.bg, 1f to p.bgDark, center = Offset(540f, 800f), radius = 1600f)

// ---------------------------------------------------------------- icons (Material paths)

private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.White)).build()

object Icons {
    val undo = icon("undo", "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z")
    val settings = icon("settings", "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z")
    val calendar = icon("calendar", "M9,11L7,11v2h2v-2zM13,11h-2v2h2v-2zM17,11h-2v2h2v-2zM19,4h-1L18,2h-2v2L8,4L8,2L6,2v2L5,4c-1.11,0 -1.99,0.9 -1.99,2L3,20c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2L21,6c0,-1.1 -0.9,-2 -2,-2zM19,20L5,20L5,9h14v11z")
    val bulb = icon("bulb", "M9,21c0,0.55 0.45,1 1,1h4c0.55,0 1,-0.45 1,-1v-1L9,20v1zM12,2C8.14,2 5,5.14 5,9c0,2.38 1.19,4.47 3,5.74L8,17c0,0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1v-2.26c1.81,-1.27 3,-3.36 3,-5.74 0,-3.86 -3.14,-7 -7,-7z")
    val add = icon("add", "M13,7h-2v4L7,11v2h4v4h2v-4h4v-2h-4L13,7zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z")
    val trophy = icon("trophy", "M19,5h-2V3H7v2H5C3.9,5 3,5.9 3,7v1c0,2.55 1.92,4.63 4.39,4.94c0.63,1.5 1.98,2.63 3.61,2.96V19H7v2h10v-2h-4v-3.1c1.63,-0.33 2.98,-1.46 3.61,-2.96C19.08,12.63 21,10.55 21,8V7C21,5.9 20.1,5 19,5zM5,8V7h2v3.82C5.84,10.4 5,9.3 5,8zM19,8c0,1.3 -0.84,2.4 -2,2.82V7h2V8z")
    val home = icon("home", "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")
    val check = icon("check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")
    val recycle = icon("recycle", "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z")
    val left = icon("left", "M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z")
    val right = icon("right", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")
    val bolt = icon("bolt", "M7,2v11h3v9l7,-12h-4l4,-8z")
}

// ---------------------------------------------------------------- card face

/**
 * Card face with an extra-large index, like dad's app: rank and suit fill the top strip, so a card is
 * readable even when only that strip shows in a column. Same shape and colours as `ui`'s PlayingCard,
 * whose index is smaller (0.36 of the width, here 0.5).
 */
@Composable
fun BigCard(card: dadgames.cards.Card, width: Dp, modifier: Modifier = Modifier, glow: Boolean = false) {
    val shape = RoundedCornerShape(width * 0.09f)
    val ink = dadgames.ui.suitColor(card.suit)
    val dens = androidx.compose.ui.platform.LocalDensity.current
    fun Dp.sp() = with(dens) { this@sp.toSp() }
    val ten = card.rank == dadgames.cards.Rank.TEN
    Box(
        modifier
            .size(width, cardHeight(width))
            .shadow(if (glow) 10.dp else 2.dp, shape, ambientColor = if (glow) GOLD else Color.Black, spotColor = if (glow) GOLD else Color.Black)
            .clip(shape)
            .background(Color(0xFFFFFCF4))
            .border(if (glow) 3.dp else 0.75.dp, if (glow) GOLD else Color(0x26000000), shape)
    ) {
        Row(
            Modifier.padding(start = width * 0.05f, top = width * 0.03f, end = width * 0.04f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                card.rank.short, color = ink, fontSize = (width * if (ten) 0.44f else 0.5f).sp(),
                fontWeight = FontWeight.Bold, style = dadgames.ui.centeredGlyph,
                letterSpacing = if (ten) (-0.09f).em else 0.em, maxLines = 1, softWrap = false,
            )
            Spacer(Modifier.weight(1f))
            Text(card.suit.glyph(), color = ink, fontSize = (width * 0.38f).sp(), style = dadgames.ui.centeredGlyph, maxLines = 1, softWrap = false)
        }
        Text(
            card.suit.glyph(), color = ink, fontSize = (width * 0.62f).sp(), style = dadgames.ui.centeredGlyph,
            modifier = Modifier.align(Alignment.Center).padding(top = width * 0.5f),
        )
    }
}

private fun dadgames.cards.Suit.glyph() = symbol + "︎"

