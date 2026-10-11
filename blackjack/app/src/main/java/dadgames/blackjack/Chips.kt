package dadgames.blackjack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dadgames.blackjack.core.CHIPS
import dadgames.ui.centeredGlyph
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** 559,864,578 */
fun Long.commas(): String = NumberFormat.getIntegerInstance(Locale.US).format(this)

/** Short money: 500, 2.5K, 250K, 1.5M, 2B. */
fun Long.short(): String {
    val a = kotlin.math.abs(this)
    val sign = if (this < 0) "-" else ""
    fun f(v: Double, unit: String): String {
        // One decimal, rounded down so a balance never looks bigger than it is: 559.8M, 2.5K, 250K.
        val s = "%.1f".format(Locale.US, Math.floor(v * 10) / 10)
        return sign + s.removeSuffix(".0") + unit
    }
    return when {
        a >= 1_000_000_000 -> f(a / 1e9, "B")
        a >= 1_000_000 -> f(a / 1e6, "M")
        a >= 1_000 -> f(a / 1e3, "K")
        else -> sign + a.toString()
    }
}

/** "+250K" / "-250K" / "0" */
fun Long.signed(): String = if (this > 0) "+" + short() else short()

data class ChipLook(val base: Color, val ring: Color, val ink: Color)

fun chipLook(v: Long): ChipLook = when (v) {
    500L -> ChipLook(Color(0xFF5B4BC4), Color(0xFFEDEBFF), Color(0xFF231A5E))
    1_000L -> ChipLook(Color(0xFF8DB33A), Color(0xFFF4FBE6), Color(0xFF34460D))
    2_500L -> ChipLook(Color(0xFF2D9BD6), Color(0xFFE8F6FF), Color(0xFF0B3F5E))
    5_000L -> ChipLook(Color(0xFF6B3A2E), Color(0xFFFFEFE8), Color(0xFF3A1A12))
    10_000L -> ChipLook(Color(0xFFD0457A), Color(0xFFFFEAF2), Color(0xFF5E1131))
    50_000L -> ChipLook(Color(0xFFE77B22), Color(0xFFFFF1E4), Color(0xFF5C2A02))
    100_000L -> ChipLook(Color(0xFF232323), Color(0xFFEDEDED), Color(0xFF111111))
    500_000L -> ChipLook(Color(0xFF8E2BB0), Color(0xFFF8E9FF), Color(0xFF3F0B52))
    else -> ChipLook(Color(0xFFC9A227), Color(0xFFFFF7DC), Color(0xFF4A3906))
}

/** A casino chip, drawn: coloured rim with white edge spots, cream centre, value in the middle. */
@Composable
fun Chip(value: Long, size: Dp, modifier: Modifier = Modifier, dim: Boolean = false, label: String = value.short()) {
    val look = chipLook(value)
    val alpha = if (dim) 0.35f else 1f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            val c = center
            drawCircle(Color(0x55000000).copy(alpha = 0.33f * alpha), r, c + Offset(0f, r * 0.06f))
            drawCircle(look.base.copy(alpha = alpha), r * 0.97f, c)
            // edge spots
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0 + 22.5)
                val p = Offset(c.x + (r * 0.82f) * cos(a).toFloat(), c.y + (r * 0.82f) * sin(a).toFloat())
                drawCircle(look.ring.copy(alpha = alpha), r * 0.12f, p)
            }
            drawCircle(look.ring.copy(alpha = 0.9f * alpha), r * 0.62f, c)
            drawCircle(look.base.copy(alpha = 0.55f * alpha), r * 0.62f, c, style = Stroke(r * 0.05f))
            drawArc(Color.White.copy(alpha = 0.18f * alpha), 200f, 120f, false, Offset(c.x - r * 0.9f, c.y - r * 0.9f), Size(r * 1.8f, r * 1.8f), style = Stroke(r * 0.06f))
        }
        val fs = with(LocalDensity.current) { (size * if (label.length <= 2) 0.34f else if (label.length == 3) 0.26f else 0.20f).toSp() }
        Text(label, color = look.ink.copy(alpha = alpha), fontSize = fs, fontWeight = FontWeight.ExtraBold, style = centeredGlyph, maxLines = 1, softWrap = false)
    }
}

/** Chips that make up [amount], largest first (for drawing a stack). */
fun chipBreakdown(amount: Long, max: Int = 8): List<Long> {
    val out = ArrayList<Long>()
    var left = amount
    for (v in CHIPS.reversed()) {
        while (left >= v && out.size < max) { out.add(v); left -= v }
    }
    if (out.isEmpty() && amount > 0) out.add(CHIPS.first())
    return out
}

/** A small stack of chips for [amount]: biggest denominations on top, like a real bet. */
@Composable
fun ChipStack(amount: Long, size: Dp, modifier: Modifier = Modifier) {
    val chips = chipBreakdown(amount, 6).sorted()
    val step = size * 0.09f
    Box(modifier.size(size, size + step * 5)) {
        for ((i, v) in chips.withIndex()) {
            Chip(v, size, Modifier.align(Alignment.BottomCenter).offset(y = -step * i))
        }
    }
}
