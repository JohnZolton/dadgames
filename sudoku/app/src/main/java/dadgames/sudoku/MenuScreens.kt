package dadgames.sudoku

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dadgames.sudoku.core.Difficulty
import dadgames.sudoku.core.Generator
import dadgames.sudoku.core.Status
import dadgames.sudoku.core.formatTime
import dadgames.ui.BigButton
import dadgames.ui.Chip
import dadgames.ui.ConfirmTextButton
import dadgames.ui.P
import dadgames.ui.PrimaryButton
import dadgames.ui.ResultPanel
import dadgames.ui.ScreenScaffold
import dadgames.ui.Section
import dadgames.ui.StatsHeadline
import dadgames.ui.Tile
import dadgames.ui.TileRow
import dadgames.ui.Toggle
import dadgames.ui.centeredGlyph
import dadgames.ui.menuBackground
import java.time.LocalDate
import java.time.YearMonth

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(vm: GameViewModel) {
    Box(Modifier.fillMaxSize().menuBackground().systemBarsPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Logo()
            Spacer(Modifier.height(18.dp))
            Text("Sudoku", style = MaterialTheme.typography.headlineLarge, color = P.text)
            val s = vm.stats
            Text(
                when {
                    s.totalWins == 0 -> "A quiet puzzle, no ads"
                    else -> "You've solved ${s.totalWins} ${if (s.totalWins == 1) "puzzle" else "puzzles"}"
                },
                color = P.textDim, fontSize = 17.sp,
            )
            Spacer(Modifier.height(36.dp))
            val g = vm.game
            if (vm.canResume && g != null) {
                ContinueButton("${g.title()}  ·  ${formatTime(g.elapsedMs)}") { vm.resume() }
                Spacer(Modifier.height(14.dp))
                BigButton("New game", primary = false) { vm.choosingDifficulty = true }
            } else {
                BigButton("New game", primary = true) { vm.choosingDifficulty = true }
            }
            Spacer(Modifier.height(14.dp))
            val today = LocalDate.now()
            BigButton(if (today.toString() in s.dailyDone) "Daily challenge  ·  done" else "Daily challenge", primary = false) {
                vm.dailyDay = today
                vm.screen = Screen.DAILY
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.widthIn(max = 360.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { BigButton("Stats", primary = false) { vm.screen = Screen.STATS } }
                Box(Modifier.weight(1f)) { BigButton("Settings", primary = false) { vm.screen = Screen.SETTINGS } }
            }
            Spacer(Modifier.height(6.dp))
            TextButton({ vm.screen = Screen.HELP }) { Text("How to play", color = P.textDim, fontSize = 17.sp) }
        }
        if (vm.choosingDifficulty) DifficultyPanel(vm)
    }
}

/** Big primary pill with the game in progress on a second line, so long titles never clip. */
@Composable
private fun ContinueButton(detail: String, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick, Modifier.fillMaxWidth().widthIn(max = 360.dp).height(72.dp), shape = RoundedCornerShape(50),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = P.accent, contentColor = P.onAccent),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Continue", fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Text(detail, fontSize = 15.sp, maxLines = 1, softWrap = false, color = P.onAccent.copy(alpha = 0.85f))
        }
    }
}

/** Little 3x3 corner of a sudoku, in the board's own colours. */
@Composable
private fun Logo() {
    val digits = listOf(5, 3, 0, 6, 0, 2, 0, 9, 8)
    Column(
        Modifier.size(132.dp).clip(RoundedCornerShape(22.dp)).background(P.panel)
            .border(2.5.dp, P.text.copy(alpha = 0.85f), RoundedCornerShape(22.dp)),
    ) {
        for (r in 0 until 3) Row(Modifier.weight(1f)) {
            for (c in 0 until 3) {
                val i = r * 3 + c
                Box(
                    Modifier.weight(1f).fillMaxSize().background(if (i == 4) P.accent.copy(alpha = 0.3f) else Color.Transparent)
                        .border(0.5.dp, P.text.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val d = digits[i]
                    if (d != 0) Text("$d", fontSize = 30.sp, style = centeredGlyph, fontWeight = FontWeight.Bold, color = P.text)
                    if (i == 4) Text("4", fontSize = 30.sp, style = centeredGlyph, fontWeight = FontWeight.Medium, color = P.accent)
                }
            }
        }
    }
}

@Composable
private fun DifficultyPanel(vm: GameViewModel) {
    ResultPanel("New game") {
        val g = vm.game
        if (vm.canResume && g != null && g.moves > 0) {
            Text("Your game in progress will end.", color = P.textDim, fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
        }
        for (d in Difficulty.entries) {
            val ls = vm.stats.of(d)
            Column(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (d == vm.settings.lastDifficulty) P.accent.copy(alpha = 0.14f) else P.wash)
                    .clickable { vm.newGame(d) }.padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.label, color = P.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                    if (ls.wins > 0) Text("best ${formatTime(ls.bestMs)}", color = P.textDim, fontSize = 15.sp, maxLines = 1)
                }
                Text(d.note, color = P.textDim, fontSize = 14.sp, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton({ vm.choosingDifficulty = false }) { Text("Cancel", color = P.textDim, fontSize = 17.sp) }
    }
}

// ---------------------------------------------------------------- daily calendar

@Composable
fun DailyScreen(vm: GameViewModel) {
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(vm.dailyDay)) }
    val done = vm.stats.dailyDone
    ScreenScaffold("Daily", { vm.screen = Screen.HOME }) {
        Spacer(Modifier.height(8.dp))
        Text(
            "A new puzzle every day, the same on every phone. Easy early in the week, harder at the weekend.",
            color = P.textDim, fontSize = 15.sp, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.wash).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ month = month.minusMonths(1) }, enabled = month > YearMonth.of(2025, 1)) { Text("‹", fontSize = 30.sp, color = P.text) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${month.atDay(1).monthName()} ${month.year}", color = P.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    val n = (1..month.lengthOfMonth()).count { month.atDay(it).toString() in done }
                    Text("$n done", color = P.textDim, fontSize = 14.sp)
                }
                val canNext = month < YearMonth.from(today)
                TextButton({ month = month.plusMonths(1) }, enabled = canNext) { Text("›", fontSize = 30.sp, color = if (canNext) P.text else P.textDim.copy(alpha = 0.3f)) }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                for (w in listOf("S", "M", "T", "W", "T", "F", "S")) {
                    Text(w, Modifier.weight(1f), color = P.textDim, fontSize = 15.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(4.dp))
            val offset = month.atDay(1).dayOfWeek.value % 7 // Sunday first
            val cells = offset + month.lengthOfMonth()
            for (week in 0 until (cells + 6) / 7) {
                Row(Modifier.fillMaxWidth()) {
                    for (dow in 0 until 7) {
                        val dayNum = week * 7 + dow - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                            if (dayNum in 1..month.lengthOfMonth()) {
                                val day = month.atDay(dayNum)
                                DayCell(day, day.toString() in done, day == today, day == vm.dailyDay, !day.isAfter(today)) { vm.dailyDay = day }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val day = vm.dailyDay
        val key = day.toString()
        val diff = Generator.dailyDifficulty(day.dayOfWeek.value)
        val g = vm.game
        val inProgress = g != null && g.daily == key && g.status != Status.WON
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.wash).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${day.weekdayName()}, ${day.monthName()} ${day.dayOfMonth}", color = P.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                diff.label + when {
                    key in done -> " · done"
                    inProgress -> " · in progress, ${formatTime(g!!.elapsedMs)}"
                    else -> ""
                },
                color = P.textDim, fontSize = 16.sp,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                when { inProgress -> "Continue"; key in done -> "Play again"; else -> "Play" },
                Modifier.fillMaxWidth().widthIn(max = 320.dp).height(56.dp),
            ) { vm.playDaily(day) }
            if (vm.canResume && !inProgress && (g?.moves ?: 0) > 0) {
                Text("Your game in progress will end.", color = P.textDim, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, done: Boolean, today: Boolean, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg = when { done -> P.accent; selected -> P.accent.copy(alpha = 0.18f); else -> Color.Transparent }
    Box(
        Modifier.fillMaxSize().clip(CircleShape).background(bg)
            .then(if (selected) Modifier.border(2.5.dp, P.text, CircleShape) else if (today) Modifier.border(2.dp, P.accent, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${day.dayOfMonth}", fontSize = 17.sp, style = centeredGlyph,
            fontWeight = if (today || done) FontWeight.Bold else FontWeight.Normal,
            color = when { done -> P.onAccent; !enabled -> P.textDim.copy(alpha = 0.3f); else -> P.text },
        )
    }
}

// ---------------------------------------------------------------- stats

@Composable
fun StatsScreen(vm: GameViewModel) {
    var level by remember { mutableStateOf(vm.settings.lastDifficulty) }
    val s = vm.stats
    val ls = s.of(level)
    ScreenScaffold("Your stats", { vm.screen = Screen.HOME }) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            for (d in Difficulty.entries) Chip(d.label, d == level) { level = d }
        }
        Spacer(Modifier.height(14.dp))
        StatsHeadline(
            if (ls.games == 0) null else ls.wins.toFloat() / ls.games,
            if (ls.games == 0) "–" else "${ls.winRate}%", "win rate",
            "${ls.wins}" to if (ls.wins == 1) "game won" else "games won",
            "${ls.games}" to if (ls.games == 1) "game played" else "games played",
        )
        Spacer(Modifier.height(14.dp))
        TileRow(Tile(if (ls.wins == 0) "–" else formatTime(ls.bestMs), "best time", highlight = ls.wins > 0), Tile(if (ls.wins == 0) "–" else formatTime(ls.averageMs), "average time"))
        TileRow(Tile("${ls.streak}", if (ls.streak == 1) "win in a row" else "wins in a row"), Tile("${ls.bestStreak}", "best streak"))
        TileRow(Tile("${ls.perfectWins}", "perfect games"), Tile("${s.dailyDone.size}", if (s.dailyDone.size == 1) "daily done" else "dailies done"))

        if (s.recent.isNotEmpty()) {
            Section("Recent games") {
                val fmt = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
                for (r in s.recent) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(if (r.won) P.accent else P.text.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (r.won) LineIcon(Glyph.CHECK, P.onAccent, 22.dp)
                            else Text("–", color = P.text, fontWeight = FontWeight.Bold, fontSize = 18.sp, style = centeredGlyph)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                r.difficulty.label + (if (r.daily != null) " · daily" else "") + " · " + formatTime(r.ms),
                                color = P.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            )
                            Text(
                                fmt.format(java.util.Date(r.time)) + if (r.won) " · ${r.mistakes} mistakes" else " · not finished",
                                color = P.textDim, fontSize = 14.sp, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ConfirmTextButton("Reset stats", "Tap again to erase all stats") { vm.resetStats() }
        }
    }
}

// ---------------------------------------------------------------- settings

@Composable
fun SettingsScreen(vm: GameViewModel) {
    val s = vm.settings
    fun set(n: AppSettings) = vm.updateSettings(n)
    ScreenScaffold("Settings", { vm.screen = Screen.HOME }) {
        Section("Sound") {
            Toggle("Sound effects", "A soft click when you place a number, a chime when you finish.", s.sound) { set(s.copy(sound = it)) }
        }
        Section("Mistakes") {
            Toggle("Show mistakes", "Wrong numbers turn red as soon as you place them.", s.autoCheck) { set(s.copy(autoCheck = it)) }
            Toggle("Three-mistake limit", "After three mistakes, take one back or start over.", s.mistakeLimit, enabled = s.autoCheck) { set(s.copy(mistakeLimit = it)) }
        }
        Section("Help on the board") {
            Toggle("Highlight row and column", "Shade the row, column and box of the square you picked.", s.highlightAreas) { set(s.copy(highlightAreas = it)) }
            Toggle("Highlight same numbers", "Shade every square holding the number you picked.", s.highlightSame) { set(s.copy(highlightSame = it)) }
            Toggle("Highlight duplicates", "Shade a number in red when it repeats in a row, column or box.", s.highlightDuplicates) { set(s.copy(highlightDuplicates = it)) }
            Toggle("Auto-remove notes", "Placing a number rubs it out of the notes in its row, column and box.", s.autoRemoveNotes) { set(s.copy(autoRemoveNotes = it)) }
        }
        Section("Timer") {
            Toggle("Show timer", "Your time is still kept for your stats when it's hidden.", s.showTimer) { set(s.copy(showTimer = it)) }
        }
    }
}

// ---------------------------------------------------------------- help

@Composable
fun HelpScreen(vm: GameViewModel) {
    ScreenScaffold("How to play", { vm.screen = Screen.HOME }) {
        val paras = listOf(
            "The goal" to "Fill every empty square with a number from 1 to 9 so that each row, each column and each of the nine 3×3 boxes has every number exactly once.",
            "Placing numbers" to "Tap a square, then tap a number at the bottom. The small figure under each number shows how many are still to place; a number disappears once all nine are in.",
            "Notes" to "Turn on Notes to pencil small numbers into a square while you work it out. Tap a number again to rub it out. With auto-remove on, placing a number clears it from the notes nearby.",
            "Mistakes" to "With Show mistakes on, a wrong number turns red and counts as a mistake. After three, you can take it back and carry on, or start a new puzzle. You can change this in Settings.",
            "Undo, Erase and Hint" to "Undo takes back your last step. Erase clears the square you picked. Hint fills in one square and says why it has to be that number.",
            "Difficulty" to "Easy puzzles only need you to spot the one place a number can go. Medium needs pairs, Hard needs patterns like X-wings, and Expert needs longer chains of reasoning. Every puzzle has exactly one answer.",
            "Daily challenge" to "A new puzzle each day. Finished days get a blue circle on the calendar, and you can go back and play days you missed.",
            "Pause" to "Pause stops the clock and hides the board. The game is saved after every move, so you can close the app any time and pick up where you left off.",
        )
        for ((h, body) in paras) {
            Spacer(Modifier.height(20.dp))
            Text(h, color = P.accent, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = P.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ---------------------------------------------------------------- busy

@Composable
fun BusyOverlay(text: String) {
    ResultPanel("One moment") {
        Text(text, color = P.text, fontSize = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.CircularProgressIndicator(color = P.accent)
    }
}
