package dadgames.blocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Board and block colours, matched by eye to Woodoku's (research/shots/w06). */
object Wood {
    val table = Color(0xFFB8733F)
    val tableDark = Color(0xFF8E5129)
    val frame = Color(0xFFD39A62)
    val frameDark = Color(0xFF9C6234)
    val boxDark = Color(0xFF5B2414)
    val boxLight = Color(0xFF8F3B22)
    val groove = Color(0xFFB8774A)
    val block = Color(0xFFE9C793)
    val blockLight = Color(0xFFF7E1B5)
    val blockDark = Color(0xFFB98A55)
    val blockEdge = Color(0xFF8A5D31)
    val glow = Color(0xFFFFD36B)
    val scoreBox = Color(0xFF5E2614)
    val todayBox = Color(0xFFD9692E)
    val gem = Color(0xFFD81B60)
    val gemLight = Color(0xFFFF8FB8)
    val gemDark = Color(0xFF7B0D3A)
    val crown = Color(0xFFF2C46B)
}

fun Modifier.woodBackground(): Modifier = background(Brush.verticalGradient(listOf(Wood.table, Wood.tableDark)))

/** A raised wooden block filling the square at ([x],[y]) of side [s]. */
fun DrawScope.woodBlock(x: Float, y: Float, s: Float, alpha: Float = 1f, glow: Boolean = false) {
    val r = CornerRadius(s * 0.1f)
    val edge = if (glow) Color(0xFFB8862A) else Wood.blockEdge
    drawRoundRect(edge, Offset(x, y), Size(s, s), r, alpha = alpha)
    val bevel = s * 0.075f
    drawRoundRect(
        Brush.linearGradient(
            if (glow) listOf(Color(0xFFFFF0B8), Wood.glow) else listOf(Wood.blockLight, Wood.block, Wood.blockDark),
            start = Offset(x, y), end = Offset(x + s, y + s),
        ),
        Offset(x, y), Size(s - bevel, s - bevel), r, alpha = alpha,
    )
    // A little grain.
    val grain = Color(0x22704418)
    for (k in 1..2) {
        val gy = y + s * (0.3f + 0.28f * k - 0.2f)
        drawLine(grain, Offset(x + s * 0.18f, gy), Offset(x + s * 0.72f, gy + s * 0.05f), strokeWidth = s * 0.035f, alpha = alpha)
    }
}

/** A faceted red gem centred in the square at ([x],[y]) of side [s]. */
fun DrawScope.gem(x: Float, y: Float, s: Float, alpha: Float = 1f, scale: Float = 1f) {
    val cx = x + s / 2
    val cy = y + s / 2
    val r = s * 0.34f * scale
    val outer = Path().apply {
        for (i in 0 until 8) {
            val a = PI / 8 + i * PI / 4
            val px = cx + (r * cos(a)).toFloat()
            val py = cy + (r * sin(a)).toFloat()
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    drawPath(outer, Wood.gemDark, alpha = alpha)
    val inner = Path().apply {
        val ri = r * 0.78f
        for (i in 0 until 8) {
            val a = PI / 8 + i * PI / 4
            val px = cx + (ri * cos(a)).toFloat()
            val py = cy + (ri * sin(a)).toFloat()
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    drawPath(inner, Brush.linearGradient(listOf(Wood.gemLight, Wood.gem), Offset(cx - r, cy - r), Offset(cx + r, cy + r)), alpha = alpha)
    drawRect(Color.White.copy(alpha = 0.45f * alpha), Offset(cx - r * 0.35f, cy - r * 0.35f), Size(r * 0.7f, r * 0.7f))
}

/** Crown with three points, fitted to the box ([x],[y],[w],[h]). */
fun DrawScope.crown(x: Float, y: Float, w: Float, h: Float, color: Color = Wood.crown) {
    val p = Path().apply {
        moveTo(x, y + h * 0.3f)
        lineTo(x + w * 0.27f, y + h * 0.62f)
        lineTo(x + w * 0.5f, y)
        lineTo(x + w * 0.73f, y + h * 0.62f)
        lineTo(x + w, y + h * 0.3f)
        lineTo(x + w * 0.9f, y + h)
        lineTo(x + w * 0.1f, y + h)
        close()
    }
    drawPath(p, color)
}

enum class Icon { BACK, GEAR, CROWN, GEM, CHECK }

@Composable
fun IconGlyph(icon: Icon, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        when (icon) {
            Icon.BACK -> {
                val p = Path().apply { moveTo(w * 0.62f, w * 0.2f); lineTo(w * 0.32f, w * 0.5f); lineTo(w * 0.62f, w * 0.8f) }
                drawPath(p, color, style = Stroke(w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Icon.GEAR -> {
                val c = Offset(w / 2, w / 2)
                val teeth = 8
                val ro = w * 0.46f
                val ri = w * 0.34f
                val p = Path()
                for (i in 0 until teeth * 2) {
                    val a0 = (i * PI / teeth) - PI / (teeth * 2.6)
                    val a1 = (i * PI / teeth) + PI / (teeth * 2.6)
                    val r = if (i % 2 == 0) ro else ri
                    val x0 = c.x + (r * cos(a0)).toFloat(); val y0 = c.y + (r * sin(a0)).toFloat()
                    val x1 = c.x + (r * cos(a1)).toFloat(); val y1 = c.y + (r * sin(a1)).toFloat()
                    if (i == 0) p.moveTo(x0, y0) else p.lineTo(x0, y0)
                    p.lineTo(x1, y1)
                }
                p.close()
                drawPath(p, color)
                drawCircle(Wood.frame, w * 0.14f, c)
            }
            Icon.CROWN -> crown(0f, w * 0.15f, w, w * 0.7f, color)
            Icon.GEM -> gem(0f, 0f, w, scale = 1.35f)
            Icon.CHECK -> {
                val p = Path().apply { moveTo(w * 0.2f, w * 0.52f); lineTo(w * 0.42f, w * 0.74f); lineTo(w * 0.8f, w * 0.28f) }
                drawPath(p, color, style = Stroke(w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Square raised wooden button with a drawn icon (top bar back / settings). */
@Composable
fun WoodIconButton(icon: Icon, description: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(52.dp).shadow(4.dp, shape).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFFF0C793), Wood.frame)))
            .border(2.dp, Wood.frameDark, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { IconGlyph(icon, 30.dp, Color(0xFF5B2A12)) }
}

/** Text size in dp that ignores the system font scale, for text inside fixed boxes and cells. [BEST_PRACTICES #12a] */
val Int.fixedSp: androidx.compose.ui.unit.TextUnit
    @Composable get() = with(androidx.compose.ui.platform.LocalDensity.current) { this@fixedSp.dp.toSp() }
