package dadgames.solitaire

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dadgames.ui.P
import dadgames.ui.PrimaryButton
import dadgames.ui.ResultPanel
import dadgames.ui.SecondaryButton
import dadgames.ui.Serif

import solitaire.core.Klondike
import solitaire.core.clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GameScreen(vm: GameViewModel) {
    val g = vm.game ?: return
    Box(Modifier.fillMaxSize().background(tableBrush(P))) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Header(vm, g)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // Banners and the auto-complete button take space from the board, so long columns squeeze
                // up above them instead of being covered. [review]
                val density = androidx.compose.ui.platform.LocalDensity.current.density
                var overlayPx by remember { mutableIntStateOf(0) }
                Board(vm, g, reserveBottom = overlayPx / density)
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .onSizeChanged { overlayPx = it.height }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedVisibility(g.canAutoComplete && !vm.autoCompleting, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                        PrimaryButton("Auto-complete", Modifier.padding(bottom = 6.dp)) { vm.autoComplete() }
                    }
                    val h = vm.hint
                    AnimatedVisibility(h != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
                        Banner { Text(h?.text ?: "", color = P.text, fontSize = 18.sp, textAlign = TextAlign.Center, lineHeight = 24.sp) }
                    }
                    AnimatedVisibility(vm.noMoves, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                        Banner {
                            Text("No moves left", color = P.accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Undo some moves, or start a new game.", color = P.textDim, fontSize = 16.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SecondaryButton("Undo", Modifier.weight(1f), enabled = g.canUndo) { vm.dismissNoMoves(); vm.undo() }
                                PrimaryButton("New game", Modifier.weight(1.3f)) { vm.newGame() }
                            }
                        }
                    }
                    if (g.daily != null && vm.hint == null && !vm.noMoves && !g.canAutoComplete) {
                        Text("Daily challenge · ${dayLabel(g.daily!!)}", color = P.textDim, fontSize = 15.sp)
                    }
                }
            }
            BottomBar(vm, g)
        }
        if (g.won && !vm.celebrating) WinPanel(vm, g)
        if (vm.preparing != null) ResultPanel("Shuffling…") {
            Text("Dealing the daily challenge for ${dayLabel(vm.preparing!!)}.", color = P.textDim, fontSize = 17.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Banner(content: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 400.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.panel.copy(alpha = 0.94f)).padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun Header(vm: GameViewModel, g: Klondike) {
    Row(Modifier.fillMaxWidth().height(48.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable { vm.go(Screen.HOME) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.home, "Menu", tint = P.textDim, modifier = Modifier.size(24.dp)) }
        HeaderStat("Time", clock(vm.clockSec), Modifier.weight(1f))
        HeaderStat("Score", "${g.score}", Modifier.weight(1f))
        HeaderStat("Moves", "${g.moves}", Modifier.weight(1f))
    }
}

@Composable
private fun HeaderStat(label: String, value: String, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center) {
        // Fixed sizes (dp, not sp): this row has exactly the screen's width to work with. [BEST_PRACTICES #12a, #12g]
        Text("$label ", color = P.textDim, fontSize = fixedSp(15f), maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
        Text(value, color = P.text, fontSize = fixedSp(21f), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
    }
}

@Composable
private fun BottomBar(vm: GameViewModel, g: Klondike) {
    Row(
        Modifier.fillMaxWidth().background(Color(0x59000000)).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(Icons.undo, "Undo", Color(0xFFF26B7A), Modifier.weight(1f), enabled = g.canUndo && !vm.autoCompleting) { vm.undo() }
        BarButton(Icons.settings, "Settings", Color(0xFF5AAEEA), Modifier.weight(1f)) { vm.go(Screen.SETTINGS) }
        BarButton(Icons.calendar, "Daily", GOLD, Modifier.weight(1f)) { vm.go(Screen.DAILY) }
        BarButton(Icons.bulb, "Hint", Color(0xFFFFE07A), Modifier.weight(1f), enabled = !g.won && !vm.autoCompleting) { vm.requestHint() }
        BarButton(Icons.add, "New game", Color.White, Modifier.weight(1.25f)) { vm.requestNewGame() }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, tint: Color, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val a = if (enabled) 1f else 0.3f
        Icon(icon, null, tint = tint.copy(alpha = a), modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = P.text.copy(alpha = a), fontSize = fixedSp(15f), fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun WinPanel(vm: GameViewModel, g: Klondike) {
    val info = vm.winInfo
    ResultPanel("You won!") {
        Text("${g.finalScore}", color = P.text, fontSize = 52.sp, fontWeight = FontWeight.Bold, fontFamily = Serif)
        Text("points", color = P.textDim, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        when {
            info?.replay == true -> Text("Replay of a solved daily: not counted in your stats", color = P.textDim, fontSize = 16.sp, textAlign = TextAlign.Center)
            info?.newBest == true -> Text("New best score!", color = P.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            info?.rank != null -> Text("Number ${info.rank + 1} in your top 10", color = P.accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.widthIn(max = 280.dp).fillMaxWidth()) {
            PanelRow("Score", "${g.score}")
            PanelRow("Time bonus", "+${g.bonus}")
            PanelRow("Time", clock(g.seconds))
            PanelRow("Moves", "${g.moves}")
        }
        Spacer(Modifier.height(14.dp))
        g.daily?.let { key ->
            val dt = LocalDate.parse(key)
            Text(
                "Daily challenge solved · ${vm.daily.solvedIn(dt.year, dt.monthValue)} of ${dt.lengthOfMonth()} this month",
                color = P.text, fontSize = 16.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
        }
        val s = vm.statsFor(g.draw)
        Text("Draw ${g.draw}: ${s.won} of ${s.played} won (${s.winRate}%)", color = P.textDim, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (g.daily != null) SecondaryButton("Calendar", Modifier.weight(1f)) { vm.go(Screen.DAILY) }
            else SecondaryButton("Menu", Modifier.weight(1f)) { vm.go(Screen.HOME) }
            PrimaryButton("New game", Modifier.weight(1.2f)) { vm.newGame() }
        }
    }
}

@Composable
private fun PanelRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = P.textDim, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(value, color = P.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** "October 11". */
fun dayLabel(key: String): String = LocalDate.parse(key).format(DateTimeFormatter.ofPattern("MMMM d", Locale.US))


/** A text size that ignores the system font scale, for rows whose width is fixed by the screen. */
@Composable
fun fixedSp(dp: Float): androidx.compose.ui.unit.TextUnit = with(androidx.compose.ui.platform.LocalDensity.current) { dp.dp.toSp() }

/** "Start a new game? This one will count as a loss." [review] */
@Composable
fun LeaveConfirm(vm: GameViewModel) {
    ResultPanel("New game?") {
        Text(vm.leaveText, color = P.text, fontSize = 18.sp, textAlign = TextAlign.Center, lineHeight = 25.sp)
        Spacer(Modifier.height(20.dp))
        // Stacked full width so neither label can clip at large font sizes.
        Column(Modifier.widthIn(max = 300.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Keep playing", Modifier.fillMaxWidth()) { vm.confirmLeaveNo() }
            SecondaryButton("New game", Modifier.fillMaxWidth()) { vm.confirmLeaveYes() }
        }
    }
}
