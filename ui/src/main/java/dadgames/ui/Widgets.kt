package dadgames.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------- buttons

private val pillPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

/** Gold pill. Label never wraps. [BEST_PRACTICES #7] */
@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick, modifier.heightIn(min = 52.dp), enabled = enabled, shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = P.accent, contentColor = P.onAccent),
        contentPadding = pillPadding,
    ) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
}

/** Outlined pill. */
@Composable
fun SecondaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick, modifier.heightIn(min = 52.dp), enabled = enabled, shape = RoundedCornerShape(50),
        border = BorderStroke(1.5.dp, P.text.copy(alpha = if (enabled) 0.6f else 0.25f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = P.text),
        contentPadding = pillPadding,
    ) { Text(label, fontSize = 18.sp, maxLines = 1, softWrap = false) }
}

/** Full-width menu button for home screens. */
@Composable
fun BigButton(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val m = modifier.fillMaxWidth().widthIn(max = 360.dp).height(60.dp)
    if (primary) {
        Button(onClick, m, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = P.accent, contentColor = P.onAccent)) {
            Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    } else {
        OutlinedButton(
            onClick, m, shape = RoundedCornerShape(50),
            border = BorderStroke(1.5.dp, P.text.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = P.text),
        ) { Text(label, fontSize = 20.sp, maxLines = 1, softWrap = false) }
    }
}

/** Round icon-ish action with a word underneath (bottom action bars: Undo, Hint, New game...). */
@Composable
fun BarAction(glyph: String, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val c = if (enabled) P.text else P.textDim.copy(alpha = 0.35f)
        Text(glyph, color = c, fontSize = 26.sp, style = centeredGlyph)
        Spacer(Modifier.height(4.dp))
        Text(label, color = c, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/** A text button that needs two taps (for destructive actions like Reset). */
@Composable
fun ConfirmTextButton(label: String, confirmLabel: String, modifier: Modifier = Modifier, onConfirm: () -> Unit) {
    var armed by rememberSaveable { mutableStateOf(false) }
    TextButton({ if (armed) { onConfirm(); armed = false } else armed = true }, modifier) {
        Text(if (armed) confirmLabel else label, color = if (armed) P.red else P.textDim, fontSize = 16.sp)
    }
}

// ---------------------------------------------------------------- screens and sections

@Composable
fun ScreenScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().menuBackground().systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onBack) { Text("‹ Back", color = P.text, fontSize = 18.sp) }
                Spacer(Modifier.weight(1f))
                Text(title, style = MaterialTheme.typography.headlineSmall, color = P.text)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(80.dp))
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 32.dp),
                content = content,
            )
        }
    }
}

@Composable
fun Section(title: String, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(22.dp))
    Text(title.uppercase(), color = P.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    if (note != null) Text(note, color = P.textDim, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.wash).padding(vertical = 6.dp), content = content)
}

@Composable
fun Toggle(label: String, detail: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled) P.text else P.textDim.copy(alpha = 0.4f), fontSize = 18.sp)
            Text(detail, color = P.textDim.copy(alpha = if (enabled) 0.8f else 0.35f), fontSize = 14.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked, onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = P.accent, checkedThumbColor = P.cardFace),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Choice(label: String, options: List<Pair<String, T>>, value: T, detail: String? = null, onChange: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, color = P.text, fontSize = 18.sp)
        if (detail != null) Text(detail, color = P.textDim.copy(alpha = 0.8f), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((name, v) in options) Chip(name, v == value) { onChange(v) }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50))
            .background(if (selected) P.accent else P.text.copy(alpha = 0.15f))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = if (selected) P.onAccent else P.text, fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, softWrap = false,
        )
    }
}

@Composable
fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = P.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
        RoundButton("–", value > range.first) { onChange(value - 1) }
        Text("$value", color = P.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
        RoundButton("+", value < range.last) { onChange(value + 1) }
    }
}

@Composable
fun RoundButton(label: String, enabled: Boolean = true, size: Dp = 42.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(P.text.copy(alpha = 0.15f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) P.text else P.textDim.copy(alpha = 0.3f), fontSize = 22.sp, style = centeredGlyph) }
}

@Composable
fun LabeledSlider(label: String, valueLabel: String, value: Int, range: IntRange = 0..100, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = P.textDim, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(valueLabel, color = P.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value.toFloat(), { onChange(it.toInt()) }, valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = P.accent, activeTrackColor = P.accent, inactiveTrackColor = P.text.copy(alpha = 0.2f)),
        )
    }
}

// ---------------------------------------------------------------- stats

class Tile(val value: String, val label: String, val highlight: Boolean = false)

@Composable
fun TileRow(a: Tile, b: Tile) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (t in listOf(a, b)) {
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(if (t.highlight) P.accent else P.wash)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                val ink = if (t.highlight) P.onAccent else P.text
                Text(t.value, color = ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, maxLines = 1)
                Text(t.label, color = if (t.highlight) ink else P.textDim, fontSize = 15.sp)
            }
        }
    }
}

/** Big number with its label underneath (stacked so labels never wrap beside the number at 360 dp). */
@Composable
fun BigNumber(value: String, label: String) {
    Column {
        Text(value, color = P.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, maxLines = 1)
        Text(label, color = P.textDim, fontSize = 15.sp, maxLines = 1)
    }
}

/** Ring showing a fraction (e.g. win rate) with big text in the middle. */
@Composable
fun Ring(fraction: Float?, centerText: String, caption: String, size: Dp = 128.dp) {
    val track = P.text.copy(alpha = 0.15f)
    val fill = P.accent
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            val inset = 7.dp.toPx()
            val arc = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, tl, arc, style = stroke)
            if (fraction != null && fraction > 0f) drawArc(fill, -90f, 360f * fraction.coerceAtMost(1f), false, tl, arc, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerText, color = P.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, maxLines = 1)
            Text(caption, color = P.textDim, fontSize = 13.sp)
        }
    }
}

/** Headline card at the top of a stats screen: ring on the left, two big numbers on the right. */
@Composable
fun StatsHeadline(fraction: Float?, centerText: String, caption: String, top: Pair<String, String>, bottom: Pair<String, String>) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.wash).padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ring(fraction, centerText, caption)
        Spacer(Modifier.width(22.dp))
        Column {
            BigNumber(top.first, top.second)
            Spacer(Modifier.height(10.dp))
            BigNumber(bottom.first, bottom.second)
        }
    }
}

// ---------------------------------------------------------------- overlays

/** Dimmed full-screen scrim with a centred rounded panel (end of hand / game over / pause). */
@Composable
fun ResultPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp), color = P.panel,
            modifier = Modifier.padding(28.dp).widthIn(max = 420.dp).border(1.dp, P.panelLine, RoundedCornerShape(28.dp)),
        ) {
            // Scrolls so nothing clips at large system font sizes. [BEST_PRACTICES #12g]
            Column(Modifier.verticalScroll(rememberScrollState()).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = P.accent, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}

/** Score/value pill used in top bars. */
@Composable
fun ScorePill(label: String, value: String, highlight: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (highlight) P.accent else P.wash).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (highlight) P.onAccent else P.text
        Text(label, color = ink, fontSize = 16.sp, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Text(value, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
