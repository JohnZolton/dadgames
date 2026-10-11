package dadgames.blackjack

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/** Simple line icons drawn on a canvas (no emoji, no icon font), white on the round buttons. */
enum class Icon { STAND, SPLIT, DOUBLE, HIT, SURRENDER, INSURANCE, NO, CLEAR, DEAL, BULB, MENU, TABLES }

@Composable
fun IconGlyph(icon: Icon, modifier: Modifier, color: Color = Color.White, bg: Color = Color.Transparent) {
    Canvas(modifier) { draw(icon, color, bg) }
}

private fun DrawScope.card(x: Float, y: Float, w: Float, h: Float, c: Color, stroke: Float, fill: Color? = null) {
    if (fill != null) drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(w * 0.16f))
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(w * 0.16f), style = Stroke(stroke))
}

private fun DrawScope.draw(icon: Icon, c: Color, bg: Color) {
    val s = size.minDimension
    val sw = s * 0.085f

    when (icon) {
        Icon.STAND -> {
            // Open palm: four fingers, thumb, palm.
            val fw = s * 0.13f
            val tops = floatArrayOf(0.22f, 0.12f, 0.10f, 0.18f)
            for (i in 0..3) {
                val x = s * (0.26f + i * 0.145f)
                drawRoundRect(c, Offset(x, s * tops[i]), Size(fw, s * 0.5f), CornerRadius(fw / 2))
            }
            drawRoundRect(c, Offset(s * 0.26f, s * 0.42f), Size(s * 0.565f, s * 0.42f), CornerRadius(s * 0.18f))
            rotate(-38f, Offset(s * 0.3f, s * 0.62f)) {
                drawRoundRect(c, Offset(s * 0.06f, s * 0.56f), Size(s * 0.32f, fw), CornerRadius(fw / 2))
            }
        }
        Icon.SPLIT -> {
            rotate(-14f, Offset(s * 0.4f, s * 0.5f)) { card(s * 0.16f, s * 0.2f, s * 0.36f, s * 0.52f, c, sw) }
            rotate(14f, Offset(s * 0.6f, s * 0.5f)) { card(s * 0.48f, s * 0.2f, s * 0.36f, s * 0.52f, c, sw, fill = bg) }
        }
        Icon.DOUBLE -> {
            for ((dx, dy) in listOf(-0.12f to -0.08f, 0.12f to 0.1f)) {
                val o = Offset(s * (0.5f + dx), s * (0.5f + dy))
                drawCircle(c, s * 0.24f, o, style = Stroke(sw))
                drawCircle(c, s * 0.11f, o, style = Stroke(sw * 0.8f))
            }
        }
        Icon.HIT, Icon.DEAL -> {
            card(s * 0.16f, s * 0.24f, s * 0.42f, s * 0.58f, c, sw)
            card(s * 0.32f, s * 0.12f, s * 0.46f, s * 0.6f, c, sw, fill = bg)
            val cx = s * 0.55f; val cy = s * 0.42f; val a = s * 0.13f
            drawLine(c, Offset(cx - a, cy), Offset(cx + a, cy), sw, StrokeCap.Round)
            drawLine(c, Offset(cx, cy - a), Offset(cx, cy + a), sw, StrokeCap.Round)
        }
        Icon.SURRENDER -> {
            drawLine(c, Offset(s * 0.28f, s * 0.14f), Offset(s * 0.28f, s * 0.88f), sw, StrokeCap.Round)
            val p = Path().apply {
                moveTo(s * 0.3f, s * 0.16f)
                cubicTo(s * 0.45f, s * 0.08f, s * 0.6f, s * 0.26f, s * 0.78f, s * 0.18f)
                lineTo(s * 0.78f, s * 0.5f)
                cubicTo(s * 0.6f, s * 0.58f, s * 0.45f, s * 0.4f, s * 0.3f, s * 0.48f)
                close()
            }
            drawPath(p, c)
        }
        Icon.INSURANCE -> {
            val p = Path().apply {
                moveTo(s * 0.5f, s * 0.12f)
                lineTo(s * 0.8f, s * 0.24f)
                cubicTo(s * 0.8f, s * 0.56f, s * 0.66f, s * 0.78f, s * 0.5f, s * 0.88f)
                cubicTo(s * 0.34f, s * 0.78f, s * 0.2f, s * 0.56f, s * 0.2f, s * 0.24f)
                close()
            }
            drawPath(p, c, style = Stroke(sw))
            drawLine(c, Offset(s * 0.38f, s * 0.48f), Offset(s * 0.47f, s * 0.58f), sw, StrokeCap.Round)
            drawLine(c, Offset(s * 0.47f, s * 0.58f), Offset(s * 0.64f, s * 0.38f), sw, StrokeCap.Round)
        }
        Icon.NO, Icon.CLEAR -> {
            val a = s * 0.26f
            drawLine(c, Offset(s / 2 - a, s / 2 - a), Offset(s / 2 + a, s / 2 + a), sw * 1.3f, StrokeCap.Round)
            drawLine(c, Offset(s / 2 + a, s / 2 - a), Offset(s / 2 - a, s / 2 + a), sw * 1.3f, StrokeCap.Round)
        }
        Icon.BULB -> {
            val o = Offset(s * 0.5f, s * 0.42f)
            drawCircle(c, s * 0.2f, o, style = Stroke(sw))
            drawLine(c, Offset(s * 0.42f, s * 0.7f), Offset(s * 0.58f, s * 0.7f), sw, StrokeCap.Round)
            drawLine(c, Offset(s * 0.44f, s * 0.8f), Offset(s * 0.56f, s * 0.8f), sw, StrokeCap.Round)
            drawLine(c, Offset(s * 0.44f, s * 0.6f), Offset(s * 0.44f, s * 0.68f), sw, StrokeCap.Round)
            drawLine(c, Offset(s * 0.56f, s * 0.6f), Offset(s * 0.56f, s * 0.68f), sw, StrokeCap.Round)
            for (deg in listOf(-150.0, -120.0, -90.0, -60.0, -30.0)) {
                val r = Math.toRadians(deg)
                val d = Offset(Math.cos(r).toFloat(), Math.sin(r).toFloat())
                drawLine(c, o + d * (s * 0.29f), o + d * (s * 0.38f), sw * 0.8f, StrokeCap.Round)
            }
        }
        Icon.MENU -> for (i in 0..2) {
            val y = s * (0.3f + i * 0.2f)
            drawLine(c, Offset(s * 0.22f, y), Offset(s * 0.78f, y), sw, StrokeCap.Round)
        }
        Icon.TABLES -> {
            rotate(-12f, Offset(s * 0.5f, s * 0.6f)) { card(s * 0.18f, s * 0.22f, s * 0.36f, s * 0.52f, c, sw) }
            card(s * 0.42f, s * 0.2f, s * 0.38f, s * 0.54f, c, sw)
        }
    }

}
