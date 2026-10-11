package dadgames.sudoku

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dadgames.sudoku.core.Board
import dadgames.sudoku.core.GameState
import dadgames.sudoku.core.MISTAKE_LIMIT
import dadgames.sudoku.core.Status
import dadgames.sudoku.core.formatTime
import dadgames.ui.P
import dadgames.ui.PrimaryButton
import dadgames.ui.ResultPanel
import dadgames.ui.SecondaryButton
import dadgames.ui.centeredGlyph
import dadgames.ui.menuBackground
import java.time.LocalDate

/** Gold for the square a hint fills, the pattern its reason talks about, and that reason's area. */
private val hintGold = Color(0xFFFFD45C)
private val hintPattern = Color(0xFFFFE49A)
private val hintWash = Color(0xFFFFF3D2)

/** His numbers: a deeper blue than the accent so they read at 4.5:1 or better even on the picked square. */
private val hisInk = Color(0xFF1A4CA6)

@Composable
fun GameScreen(vm: GameViewModel) {
    val g = vm.game ?: return
    Box(Modifier.fillMaxSize().menuBackground()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            TopBar(g, vm)
            if (vm.paused) {
                PauseView(g, vm, Modifier.weight(1f))
            } else {
                InfoRow(g, vm)
                BoardView(g, vm, Modifier.padding(horizontal = 8.dp))
                MessageLine(g, vm, Modifier.weight(1f))
                Tools(g, vm)
                NumberPad(g, vm)
                Spacer(Modifier.height(8.dp))
            }
        }
        AnimatedVisibility(g.status == Status.WON && vm.win != null, enter = fadeIn(tween(350)), exit = fadeOut(tween(150))) { WinPanel(g, vm) }
        AnimatedVisibility(g.status == Status.OUT_OF_MISTAKES, enter = fadeIn(tween(250)), exit = fadeOut(tween(150))) { MistakesPanel(g, vm) }
    }
}

fun GameState.title(): String = daily?.let { val d = LocalDate.parse(it); "Daily · ${d.monthName().take(3)} ${d.dayOfMonth}" } ?: difficulty.label

// ---------------------------------------------------------------- top

@Composable
private fun TopBar(g: GameState, vm: GameViewModel) {
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp)) {
        TextButton(vm::leaveGame, Modifier.align(Alignment.CenterStart)) { Text("‹ Menu", color = P.text, fontSize = 18.sp, maxLines = 1) }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(g.title(), color = P.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            if (g.daily != null) Text(g.difficulty.label, color = P.textDim, fontSize = 14.sp, maxLines = 1)
        }
        if (g.status == Status.PLAYING) {
            Row(
                Modifier.align(Alignment.CenterEnd).clip(RoundedCornerShape(50)).clickable { vm.pause(!vm.paused) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineIcon(if (vm.paused) Glyph.PLAY else Glyph.PAUSE, P.text, 20.dp)
                Spacer(Modifier.width(6.dp))
                Text(if (vm.paused) "Resume" else "Pause", color = P.text, fontSize = 18.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun InfoRow(g: GameState, vm: GameViewModel) {
    val s = vm.settings
    Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (s.autoCheck) {
            Text("Mistakes ", color = P.textDim, fontSize = 17.sp)
            Text(
                if (s.mistakeLimit) "${g.mistakes} of $MISTAKE_LIMIT" else "${g.mistakes}",
                color = if (g.mistakes > 0) P.red else P.text, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.weight(1f))
        if (s.showTimer) Text(formatTime(vm.clockMs), color = P.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- board

@Composable
private fun BoardView(g: GameState, vm: GameViewModel, modifier: Modifier = Modifier) {
    val s = vm.settings
    val sel = vm.selected
    val selVal = sel?.let { g.values[it] }?.takeIf { it != 0 }
    val conflicts = remember(g.values, s.highlightDuplicates) { if (s.highlightDuplicates) g.conflicts() else emptySet() }
    val showWrong = s.autoCheck || g.revealWrong
    val hint = vm.hint
    val focus = remember(hint) { hint?.focus?.toSet() ?: emptySet() }
    val pattern = remember(hint) { hint?.pattern?.toSet() ?: emptySet() }
    val accent = P.accent
    val red = P.red
    val line = P.text

    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(P.panel)) {
        val cell = maxWidth / 9
        val density = LocalDensity.current
        // Sized from the cell, not the font scale, so big digits always fit their squares. [BEST_PRACTICES #10]
        val digitSp = with(density) { (cell * 0.66f).toSp() }
        val noteSp = with(density) { (cell * 0.32f).toSp() }
        Column(Modifier.fillMaxSize()) {
            for (r in 0 until 9) Row(Modifier.weight(1f).fillMaxWidth()) {
                for (c in 0 until 9) {
                    val i = r * 9 + c
                    val v = g.values[i]
                    val wrong = showWrong && g.isWrong(i)
                    val bg = when {
                        hint?.cell == i -> hintGold
                        i == sel -> accent.copy(alpha = 0.30f)
                        wrong || i in conflicts -> red.copy(alpha = 0.15f)
                        i in pattern -> hintPattern
                        s.highlightSame && selVal != null && v == selVal -> accent.copy(alpha = 0.20f)
                        i in focus -> hintWash
                        s.highlightAreas && sel != null && Board.sees(i, sel) -> accent.copy(alpha = 0.16f)
                        else -> Color.Transparent
                    }
                    val animBg by animateColorAsState(bg, tween(140), label = "cell")
                    Box(
                        Modifier.weight(1f).fillMaxSize().background(animBg)
                            .clickable(remember { MutableInteractionSource() }, null) { vm.tapCell(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (v != 0) {
                            Text(
                                "$v", fontSize = digitSp, style = centeredGlyph,
                                fontWeight = if (g.isGiven(i)) FontWeight.Bold else FontWeight.Medium,
                                color = when { g.isGiven(i) -> P.text; wrong -> red; else -> hisInk },
                            )
                        } else if (g.notes[i] != 0) {
                            Notes(g.notes[i], selVal, noteSp)
                        }
                    }
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val step = w / 9
            val thin = 1.dp.toPx()
            val thick = 2.6.dp.toPx()
            for (k in 1 until 9) {
                val isBox = k % 3 == 0
                val sw = if (isBox) thick else thin
                val col = if (isBox) line.copy(alpha = 0.85f) else line.copy(alpha = 0.2f)
                drawLine(col, Offset(k * step, 0f), Offset(k * step, w), sw)
                drawLine(col, Offset(0f, k * step), Offset(w, k * step), sw)
            }
            drawRoundRect(line.copy(alpha = 0.85f), style = Stroke(thick * 1.2f), cornerRadius = CornerRadius(8.dp.toPx()))
        }
    }
}

@Composable
private fun Notes(mask: Int, highlight: Int?, size: androidx.compose.ui.unit.TextUnit) {
    Column(Modifier.fillMaxSize().padding(2.dp)) {
        for (r in 0 until 3) Row(Modifier.weight(1f).fillMaxWidth()) {
            for (c in 0 until 3) {
                val d = r * 3 + c + 1
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (mask and (1 shl d) != 0) {
                        val hi = d == highlight
                        Text(
                            "$d", fontSize = size, style = centeredGlyph,
                            color = if (hi) hisInk else P.text.copy(alpha = 0.78f), fontWeight = if (hi) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- message, tools, pad

@Composable
private fun MessageLine(g: GameState, vm: GameViewModel, modifier: Modifier) {
    val m = vm.message
    val prompt = when {
        m != null -> m.text
        vm.selected == null -> "Tap a square, then a number."
        vm.notesMode -> "Notes on: tap numbers to pencil them in."
        else -> ""
    }
    // Fit the text to its box: start at 17sp (font scale capped at 1.15) and shrink until nothing is cut off,
    // so the answer at the end of a long hint is never lost. [BEST_PRACTICES 12g]
    val density = LocalDensity.current
    val capped = remember(density) { androidx.compose.ui.unit.Density(density.density, minOf(density.fontScale, 1.15f)) }
    var size by remember(prompt) { mutableFloatStateOf(17f) }
    var ready by remember(prompt) { mutableStateOf(false) }
    androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides capped) {
        Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text(
                prompt, textAlign = TextAlign.Center, fontSize = size.sp, lineHeight = (size * 1.3f).sp,
                color = when { m?.warn == true -> P.red; m != null -> P.text; else -> P.textDim },
                fontWeight = if (m != null) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.drawWithContent { if (ready) drawContent() },
                onTextLayout = { r -> if (r.didOverflowHeight && size > 11f) size -= 1f else ready = true },
            )
        }
    }
}

@Composable
private fun Tools(g: GameState, vm: GameViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        ToolButton(Glyph.UNDO, "Undo", enabled = g.canUndo, onClick = vm::undo)
        ToolButton(Glyph.ERASE, "Erase", onClick = vm::erase)
        ToolButton(Glyph.NOTES, if (vm.notesMode) "Notes on" else "Notes off", active = vm.notesMode, onClick = vm::toggleNotes)
        ToolButton(Glyph.HINT, "Hint", onClick = vm::requestHint)
    }
}

@Composable
private fun ToolButton(glyph: Glyph, label: String, enabled: Boolean = true, badge: String? = null, active: Boolean = false, onClick: () -> Unit) {
    val c = if (enabled) P.text else P.textDim.copy(alpha = 0.35f)
    Column(
        Modifier.width(84.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 64.dp, height = 34.dp), contentAlignment = Alignment.Center) {
            LineIcon(glyph, if (active) P.accent else c, 30.dp)
            if (badge != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp).clip(RoundedCornerShape(50))
                        .background(if (active) P.accent else P.text.copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 1.dp),
                ) { Text(badge, color = if (active) P.onAccent else P.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (active) P.accent else c, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun NumberPad(g: GameState, vm: GameViewModel) {
    val autoCheck = vm.settings.autoCheck
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        val w = maxWidth / 9
        val digitSp = with(LocalDensity.current) { (w * 0.78f).toSp() }
        Row(Modifier.fillMaxWidth().height(w * 2f)) {
            for (d in 1..9) {
                Box(Modifier.weight(1f).fillMaxSize().padding(horizontal = 1.dp), contentAlignment = Alignment.Center) {
                    if (!g.digitDone(d)) {
                        Column(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).clickable { vm.tapDigit(d) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "$d", fontSize = digitSp, style = centeredGlyph, fontWeight = FontWeight.Medium,
                                color = if (vm.notesMode) P.textDim else P.accent,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("${g.remaining(d, autoCheck)}", fontSize = 14.sp, color = P.textDim, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- pause and panels

@Composable
private fun PauseView(g: GameState, vm: GameViewModel, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Paused", style = MaterialTheme.typography.headlineLarge, color = P.text)
        Spacer(Modifier.height(10.dp))
        Text("${g.difficulty.label} · ${formatTime(vm.clockMs)}", color = P.textDim, fontSize = 19.sp)
        Text("The clock is stopped and the board is hidden.", color = P.textDim, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(36.dp))
        PrimaryButton("Resume", Modifier.fillMaxWidth().widthIn(max = 320.dp).height(60.dp)) { vm.pause(false) }
    }
}

@Composable
private fun WinPanel(g: GameState, vm: GameViewModel) {
    val w = vm.win ?: return
    val ls = vm.stats.of(g.difficulty)
    ResultPanel(if (g.daily != null) "Daily challenge done!" else "Solved!") {
        Text("${g.difficulty.label} · ${formatTime(g.elapsedMs)}", color = P.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        when {
            w.newBest && ls.wins > 1 -> Text("New best time!", color = P.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            w.previousBest > 0 -> Text("Best time ${formatTime(w.previousBest)}", color = P.textDim, fontSize = 16.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Mistakes ${g.mistakes} · Hints ${g.hints}", color = P.textDim, fontSize = 16.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "${ls.wins} ${g.difficulty.label.lowercase()} ${if (ls.wins == 1) "win" else "wins"}" +
                if (ls.streak >= 2) " · ${ls.streak} in a row" else " · ${ls.winRate}% won",
            color = P.text, fontSize = 16.sp,
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Menu", Modifier.weight(1f)) { vm.leaveGame() }
            if (g.daily != null) PrimaryButton("Calendar", Modifier.weight(1.3f)) { vm.screen = Screen.DAILY }
            else PrimaryButton("New game", Modifier.weight(1.3f)) { vm.newGame(g.difficulty) }
        }
    }
}

@Composable
private fun MistakesPanel(g: GameState, vm: GameViewModel) {
    ResultPanel("Three mistakes") {
        Text(
            "No harm done. Take back the last number and keep going, or start a fresh puzzle.",
            color = P.text, fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 24.sp,
        )
        Spacer(Modifier.height(22.dp))
        PrimaryButton("Take it back", Modifier.fillMaxWidth()) { vm.secondChance() }
        Spacer(Modifier.height(10.dp))
        SecondaryButton("New ${g.difficulty.label.lowercase()} game", Modifier.fillMaxWidth()) { vm.newGame(g.difficulty) }
        Spacer(Modifier.height(4.dp))
        TextButton(vm::leaveGame) { Text("Menu", color = P.textDim, fontSize = 17.sp) }
    }
}
