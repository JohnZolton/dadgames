package dadgames.sudoku

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Glyph { UNDO, ERASE, NOTES, HINT, PAUSE, PLAY, CHECK }

/** Simple line icons drawn in code, so they look the same on every phone (no emoji fonts). */
@Composable
fun LineIcon(g: Glyph, color: Color, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val sw = w * 0.09f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (g) {
            Glyph.UNDO -> {
                // Arc curving back to the left with an arrowhead.
                val p = Path().apply {
                    moveTo(w * 0.30f, w * 0.38f)
                    lineTo(w * 0.62f, w * 0.38f)
                    cubicTo(w * 0.95f, w * 0.38f, w * 0.95f, w * 0.85f, w * 0.62f, w * 0.85f)
                    lineTo(w * 0.35f, w * 0.85f)
                }
                drawPath(p, color, style = stroke)
                val head = Path().apply {
                    moveTo(w * 0.42f, w * 0.20f)
                    lineTo(w * 0.22f, w * 0.38f)
                    lineTo(w * 0.42f, w * 0.56f)
                }
                drawPath(head, color, style = stroke)
            }
            Glyph.ERASE -> {
                // Backspace key: a tag pointing left with an X.
                val p = Path().apply {
                    moveTo(w * 0.08f, w * 0.5f)
                    lineTo(w * 0.32f, w * 0.22f)
                    lineTo(w * 0.92f, w * 0.22f)
                    lineTo(w * 0.92f, w * 0.78f)
                    lineTo(w * 0.32f, w * 0.78f)
                    close()
                }
                drawPath(p, color, style = stroke)
                drawLine(color, Offset(w * 0.48f, w * 0.38f), Offset(w * 0.72f, w * 0.62f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * 0.72f, w * 0.38f), Offset(w * 0.48f, w * 0.62f), sw, StrokeCap.Round)
            }
            Glyph.NOTES -> {
                // Pencil at 45 degrees.
                rotate(45f) {
                    val body = Path().apply {
                        moveTo(w * 0.40f, w * 0.05f)
                        lineTo(w * 0.60f, w * 0.05f)
                        lineTo(w * 0.60f, w * 0.72f)
                        lineTo(w * 0.50f, w * 0.95f)
                        lineTo(w * 0.40f, w * 0.72f)
                        close()
                    }
                    drawPath(body, color, style = stroke)
                    drawLine(color, Offset(w * 0.40f, w * 0.20f), Offset(w * 0.60f, w * 0.20f), sw)
                }
            }
            Glyph.HINT -> {
                // Light bulb.
                drawCircle(color, w * 0.27f, Offset(w * 0.5f, w * 0.38f), style = stroke)
                drawLine(color, Offset(w * 0.38f, w * 0.68f), Offset(w * 0.62f, w * 0.68f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * 0.40f, w * 0.82f), Offset(w * 0.60f, w * 0.82f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * 0.45f, w * 0.94f), Offset(w * 0.55f, w * 0.94f), sw, StrokeCap.Round)
            }
            Glyph.PAUSE -> {
                val bw = w * 0.2f
                drawRoundRect(color, Offset(w * 0.24f, w * 0.18f), Size(bw, w * 0.64f), CornerRadius(bw * 0.3f))
                drawRoundRect(color, Offset(w * 0.56f, w * 0.18f), Size(bw, w * 0.64f), CornerRadius(bw * 0.3f))
            }
            Glyph.PLAY -> {
                val p = Path().apply { moveTo(w * 0.28f, w * 0.16f); lineTo(w * 0.84f, w * 0.5f); lineTo(w * 0.28f, w * 0.84f); close() }
                drawPath(p, color)
            }
            Glyph.CHECK -> {
                val p = Path().apply { moveTo(w * 0.2f, w * 0.52f); lineTo(w * 0.42f, w * 0.74f); lineTo(w * 0.82f, w * 0.28f) }
                drawPath(p, color, style = Stroke(width = w * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
