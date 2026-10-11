package dadgames.blocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import blocks.core.Daily
import dadgames.ui.BigButton
import dadgames.ui.ConfirmTextButton
import dadgames.ui.P
import dadgames.ui.PrimaryButton
import dadgames.ui.ScreenScaffold
import dadgames.ui.Section
import dadgames.ui.Serif
import dadgames.ui.Tile
import dadgames.ui.TileRow
import dadgames.ui.Toggle
import dadgames.ui.centeredGlyph
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val cardWood = Color(0xFFE6B987)
private val cardInk = Color(0xFF4A2412)
private val cardInkDim = Color(0xFF7A4A2A)

@Composable
private fun WoodCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().widthIn(max = 420.dp).shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
            .background(cardWood).border(2.dp, Color(0xFFC48B55), RoundedCornerShape(22.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(vm: GameViewModel) {
    Box(Modifier.fillMaxSize().woodBackground().systemBarsPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(52.dp)
                Spacer(Modifier.width(14.dp))
                Text("Blocks", style = MaterialTheme.typography.headlineLarge, color = Color(0xFFFFF1DD))
            }
            val s = vm.stats
            if (s.played > 0) {
                Text(
                    "${s.played} ${if (s.played == 1) "game" else "games"} played · average ${s.average}",
                    color = P.textDim, fontSize = 16.sp, maxLines = 1,
                )
            }
            Spacer(Modifier.height(18.dp))
            HighScoresCard(vm)
            Spacer(Modifier.height(22.dp))
            val c = vm.classic
            if (vm.canResume && c != null) {
                ContinueButton(c.score) { vm.resume() }
                Spacer(Modifier.height(12.dp))
                BigButton("New game", primary = false) { vm.newGame() }
            } else {
                BigButton("New game", primary = true) { vm.newGame() }
            }
            Spacer(Modifier.height(22.dp))
            DailyCard(vm)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.widthIn(max = 420.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { BigButton("Stats", primary = false) { vm.screen = Screen.STATS } }
                Box(Modifier.weight(1f)) { BigButton("Settings", primary = false) { vm.openSettings(Screen.HOME) } }
            }
            TextButton({ vm.openHelp(Screen.HOME) }) { Text("How to play", color = P.textDim, fontSize = 17.sp) }
        }
    }
}

@Composable
fun Logo(size: Dp) {
    Canvas(Modifier.size(size)) {
        val p = this.size.width / 3f
        val filled = setOf(0, 3, 4, 5, 7)
        for (i in 0 until 9) {
            val x = (i % 3) * p
            val y = (i / 3) * p
            if (i in filled) woodBlock(x + p * 0.04f, y + p * 0.04f, p * 0.92f)
            else drawRect(if (i % 2 == 0) Wood.boxDark else Wood.boxLight, Offset(x + p * 0.06f, y + p * 0.06f), Size(p * 0.88f, p * 0.88f))
        }
    }
}

@Composable
private fun HighScoresCard(vm: GameViewModel) {
    val d = vm.today()
    val hs = vm.highScores
    WoodCard {
        Text("MY HIGH SCORES", color = cardInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            for ((label, v) in listOf("Today" to hs.todayBest(d), "This week" to hs.weekBest(d), "This month" to hs.monthBest(d), "This year" to hs.yearBest(d))) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = cardInkDim, fontSize = 13.fixedSp, maxLines = 1, softWrap = false)
                    Text("$v", color = cardInk, fontSize = 25.fixedSp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF6E3A1C)).padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconGlyph(Icon.CROWN, 28.dp, Wood.crown)
            Spacer(Modifier.width(10.dp))
            Text("Best ever", color = Color(0xFFFFE6C4), fontSize = 17.sp)
            Spacer(Modifier.width(10.dp))
            Text("${hs.ever}", color = Color(0xFFFFF4E6), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DailyCard(vm: GameViewModel) {
    val today = vm.today()
    val wins = vm.daily.winsIn(today.toString().substring(0, 7))
    val solved = today.toString() in vm.daily.won
    WoodCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconGlyph(Icon.GEM, 26.dp, Wood.gem)
            Spacer(Modifier.width(10.dp))
            Text("Daily challenge", color = cardInk, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Milestones(wins, onCard = true)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                when { vm.preparing -> "Setting up…"; solved -> "Solved today"; else -> "Play today" },
                Modifier.weight(1.2f), enabled = !vm.preparing,
            ) { if (solved) { vm.calendarDate = today; vm.screen = Screen.DAILY } else vm.playDaily(today) }
            OutlinedButton(
                { vm.calendarDate = today; vm.screen = Screen.DAILY }, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(50),
                border = BorderStroke(2.dp, cardInk.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = cardInk),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { Text("Calendar", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) }
        }
    }
}

/** Monthly trophies at 1 / 3 / 10 / 24 daily wins. */
@Composable
fun Milestones(wins: Int, onCard: Boolean = false) {
    val track = if (onCard) Color(0xFF6E3A1C) else Color(0x55000000)
    Box(Modifier.fillMaxWidth().widthIn(max = 380.dp).height(58.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(10.dp).clip(RoundedCornerShape(50)).background(track))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (m in Daily.milestones) {
                val done = wins >= m
                Column(
                    Modifier.size(56.dp).clip(CircleShape).background(if (done) Wood.glow else track)
                        .border(2.dp, if (done) Color(0xFFB8862A) else Color(0x33FFFFFF), CircleShape)
                        .semantics { contentDescription = "$m ${if (m == 1) "win" else "wins"}${if (done) ", reached" else ""}" },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    val ink = if (done) Color(0xFF4A2A06) else Color(0xFFFFEBD2)
                    Text("$m", color = ink, fontSize = 20.fixedSp, fontWeight = FontWeight.Bold, style = centeredGlyph)
                    Text(if (m == 1) "WIN" else "WINS", color = ink, fontSize = 10.fixedSp, fontWeight = FontWeight.Bold, style = centeredGlyph)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- daily calendar

@Composable
fun DailyScreen(vm: GameViewModel) {
    val today = vm.today()
    val sel = vm.calendarDate
    var month = sel.withDayOfMonth(1)
    ScreenScaffold("Daily challenge", onBack = { vm.screen = Screen.HOME }) {
        Spacer(Modifier.height(8.dp))
        WoodCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MonthArrow("‹", "Previous month", enabled = true) { vm.calendarDate = month.minusMonths(1) }
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.US)} ${month.year}",
                    color = cardInk, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                val canNext = month.plusMonths(1) <= today.withDayOfMonth(1)
                MonthArrow("›", "Next month", enabled = canNext) {
                    val next = month.plusMonths(1)
                    vm.calendarDate = if (next.year == today.year && next.month == today.month) today else next
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                for (d in listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")) {
                    Text(d, color = cardInkDim, fontSize = 13.fixedSp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            val lead = month.dayOfWeek.value - 1
            val days = month.lengthOfMonth()
            val weeks = (lead + days + 6) / 7
            for (w in 0 until weeks) {
                Row(Modifier.fillMaxWidth()) {
                    for (k in 0 until 7) {
                        val n = w * 7 + k - lead + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (n in 1..days) DayCell(vm, month.withDayOfMonth(n), today, sel)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val wins = vm.daily.winsIn(month.toString().substring(0, 7))
        Text(
            "$wins ${if (wins == 1) "win" else "wins"} in ${month.month.getDisplayName(TextStyle.FULL, Locale.US)}",
            color = P.text, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        Milestones(wins)
        Spacer(Modifier.height(18.dp))
        val key = sel.toString()
        val solved = key in vm.daily.won
        val inProgress = vm.dailyGame?.let { it.date == key && !it.over && it.moves > 0 } == true
        val name = "${sel.month.getDisplayName(TextStyle.FULL, Locale.US)} ${sel.dayOfMonth}"
        val label = when {
            vm.preparing -> "Setting up…"
            inProgress -> "Continue $name"
            solved -> "Play $name again"
            else -> "Play $name"
        }
        BigButton(label, primary = true, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            if (!vm.preparing) vm.playDaily(sel, restart = !inProgress)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Clear the rows, columns or 3x3 boxes that hold all the gems.",
            color = P.textDim, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MonthArrow(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = if (enabled) cardInk else cardInk.copy(alpha = 0.25f), fontSize = 34.fixedSp, style = centeredGlyph) }
}

@Composable
private fun DayCell(vm: GameViewModel, day: LocalDate, today: LocalDate, sel: LocalDate) {
    val future = day > today
    val won = day.toString() in vm.daily.won
    val isSel = day == sel
    val isToday = day == today
    val bg = when {
        won -> Wood.glow
        isSel -> Color(0xFF6E3A1C)
        else -> Color.Transparent
    }
    val ring = when {
        isToday -> Modifier.border(3.dp, Color(0xFF2E9BD6), CircleShape)
        isSel && won -> Modifier.border(3.dp, Color(0xFF6E3A1C), CircleShape)
        else -> Modifier
    }
    Box(
        Modifier.fillMaxSize().clip(CircleShape).background(bg).then(ring)
            .clickable(enabled = !future) { vm.calendarDate = day }
            .semantics { contentDescription = "$day${if (won) ", solved" else ""}" },
        contentAlignment = Alignment.Center,
    ) {
        val ink = when {
            won -> Color(0xFF4A2A06)
            isSel -> Color(0xFFFFF1DD)
            future -> cardInk.copy(alpha = 0.3f)
            else -> cardInk
        }
        Text(
            "${day.dayOfMonth}", color = ink, fontSize = 19.fixedSp, fontWeight = if (isSel || isToday || won) FontWeight.Bold else FontWeight.Normal,
            style = centeredGlyph, modifier = if (won) Modifier.padding(bottom = 10.dp) else Modifier,
        )
        if (won) IconGlyph(Icon.CHECK, 14.dp, Color(0xFF4A2A06), Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
    }
}

// ---------------------------------------------------------------- settings

@Composable
fun SettingsScreen(vm: GameViewModel) {
    val s = vm.settings
    ScreenScaffold("Settings", onBack = { vm.screen = vm.returnTo }) {
        Section("Game") {
            Toggle("Sound", "Wooden clicks when you place a piece, a chime when you clear.", s.sound) { vm.updateSettings(s.copy(sound = it)) }
            Toggle("Vibration", "A small buzz when a piece drops onto the board.", s.haptics) { vm.updateSettings(s.copy(haptics = it)) }
        }
        Section("Help") {
            Row(
                Modifier.fillMaxWidth().clickable { vm.openHelp(Screen.SETTINGS) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("How to play", color = P.text, fontSize = 18.sp)
                    Text("The rules, scoring and the daily challenge.", color = P.textDim, fontSize = 14.sp)
                }
                Text("›", color = P.textDim, fontSize = 28.sp)
            }
        }
        Section("Records", note = "Each needs two taps. Your best scores were brought over from Woodoku.") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                ConfirmTextButton("Reset stats", "Tap again to reset stats") { vm.resetStats() }
                ConfirmTextButton("Reset high scores", "Tap again to reset high scores") { vm.resetHighScores() }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Blocks ${BuildConfig.VERSION_NAME}", color = P.textDim, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

// ---------------------------------------------------------------- how to play

@Composable
fun HelpScreen(vm: GameViewModel) {
    ScreenScaffold("How to play", onBack = { vm.screen = vm.returnTo }) {
        Section("The game") {
            HelpLine("Drag a piece from the tray onto the board. It floats above your finger and a shadow shows where it will land.")
            HelpLine("Fill a whole row, column or 3x3 box to clear it.")
            HelpLine("You get three pieces at a time, and three new ones once all three are placed.")
            HelpLine("The game ends when none of your pieces fit anywhere. Faded pieces in the tray don't fit.")
            HelpLine("You can also tap a piece, tap the board to see its shadow, then tap the shadow to place it.")
        }
        Section("Scoring") {
            HelpLine("Each block you place: 1 point.")
            HelpLine("Clearing one row, column or box: 18 more. Two at once (a combo): 46. Three: 74.")
            HelpLine("Clear again on your next move to build a streak: each move in a row that clears is worth 10 more.")
        }
        Section("Daily challenge") {
            HelpLine("A new puzzle every day. Some blocks start on the board, and some of them hold gems.")
            HelpLine("Clear the rows, columns or boxes holding the gems to collect them. Collect them all to win.")
            HelpLine("Wins this month earn trophies at 1, 3, 10 and 24 wins.")
        }
        Section("Hint") {
            HelpLine("Tap Hint and the best spot for one of your pieces glows. Drag it there, or tap the glowing shadow.")
        }
    }
}

@Composable
private fun HelpLine(text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)) {
        Box(Modifier.padding(top = 9.dp).size(8.dp).clip(CircleShape).background(P.accent))
        Spacer(Modifier.width(12.dp))
        Text(text, color = P.text, fontSize = 18.sp, lineHeight = 25.sp)
    }
}

// ---------------------------------------------------------------- stats

@Composable
fun StatsScreen(vm: GameViewModel) {
    val s = vm.stats
    val hs = vm.highScores
    val d = vm.today()
    ScreenScaffold("Stats", onBack = { vm.screen = Screen.HOME }) {
        Section("High scores") {
            Column(Modifier.padding(horizontal = 10.dp)) {
                TileRow(Tile("${hs.ever}", "Best ever", highlight = true), Tile("${hs.yearBest(d)}", "This year"))
                TileRow(Tile("${hs.monthBest(d)}", "This month"), Tile("${hs.weekBest(d)}", "This week"))
                TileRow(Tile("${hs.todayBest(d)}", "Today"), Tile("${s.average}", "Average score"))
            }
        }
        Section("Games") {
            Column(Modifier.padding(horizontal = 10.dp)) {
                TileRow(Tile("${s.played}", "Games played"), Tile("%,d".format(s.cellsCleared), "Blocks cleared"))
                TileRow(Tile("${s.bestCombo}", "Biggest combo"), Tile("${s.bestStreak}", "Longest streak"))
                TileRow(Tile("%,d".format(s.regionsCleared), "Lines and boxes"), Tile("${s.dailyWins}", "Daily wins"))
            }
        }
        Section("Recent games") {
            if (s.recent.isEmpty()) {
                Text("No games yet.", color = P.textDim, fontSize = 16.sp, modifier = Modifier.padding(16.dp))
            }
            val fmt = java.text.SimpleDateFormat("MMM d, h:mm a", Locale.US)
            for (r in s.recent) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt.format(java.util.Date(r.time)), color = P.textDim, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    if (r.record.isNotEmpty()) {
                        Text(if (r.record == "ever") "Best ever" else "Best today", color = P.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text("${r.score}", color = P.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, maxLines = 1)
                }
            }
        }
    }
}

/** "Continue game" with the score on a second line, so the label never clips. [BEST_PRACTICES #12b] */
@Composable
private fun ContinueButton(score: Int, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick, Modifier.fillMaxWidth().widthIn(max = 360.dp).height(72.dp), shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = P.accent, contentColor = P.onAccent),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Continue game", fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Text("Score $score", fontSize = 15.sp, maxLines = 1, softWrap = false)
        }
    }
}
