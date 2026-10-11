package dadgames.solitaire

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dadgames.cards.Card
import dadgames.cards.Rank
import dadgames.cards.Suit
import dadgames.ui.BigButton
import dadgames.ui.Choice
import dadgames.ui.ConfirmTextButton
import dadgames.ui.P

import dadgames.ui.PrimaryButton
import dadgames.ui.ScreenScaffold
import dadgames.ui.Section
import dadgames.ui.Serif
import dadgames.ui.Ring
import dadgames.ui.Tile
import dadgames.ui.TileRow
import dadgames.ui.Toggle
import dadgames.ui.menuBackground
import solitaire.core.Daily
import solitaire.core.Scoring
import solitaire.core.clock
import solitaire.core.longClock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(vm: GameViewModel) {
    Box(Modifier.fillMaxSize().menuBackground().systemBarsPadding()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.height(170.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CardBackArt(84.dp, cardBacks.getOrElse(vm.settings.cardBack) { cardBacks[0] }, Modifier.offset(x = (-58).dp, y = 8.dp).rotate(-16f))
                BigCard(Card(Rank.KING, Suit.HEARTS), 84.dp, Modifier.offset(x = 58.dp, y = 8.dp).rotate(16f))
                BigCard(Card(Rank.ACE, Suit.SPADES), 90.dp, Modifier.offset(y = (-4).dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("Solitaire", style = MaterialTheme.typography.headlineLarge, color = P.text)
            val s = vm.stats
            Text(
                when {
                    s.played == 0 -> "Klondike, one card at a time"
                    s.streak >= 2 -> "${s.streak} wins in a row · ${s.winRate}% overall"
                    else -> "You've won ${s.won} of ${s.played} games (${s.winRate}%)"
                },
                color = P.textDim, fontSize = 17.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            if (vm.canResume) {
                BigButton("Continue game", primary = true) { vm.resume() }
                Spacer(Modifier.height(14.dp))
                BigButton("New game", primary = false) { vm.newGame() }
            } else {
                BigButton("New game", primary = true) { vm.newGame() }
            }
            Spacer(Modifier.height(14.dp))
            BigButton("Daily challenge", primary = false) { vm.go(Screen.DAILY) }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.widthIn(max = 360.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { BigButton("Stats", primary = false) { vm.go(Screen.STATS) } }
                Box(Modifier.weight(1f)) { BigButton("Settings", primary = false) { vm.go(Screen.SETTINGS) } }
            }
        }
    }
}

// ---------------------------------------------------------------- settings

@Composable
fun SettingsScreen(vm: GameViewModel) {
    val s = vm.settings
    ScreenScaffold("Settings", vm::back) {
        Section("Sound and motion") {
            Toggle("Sounds", "Soft card sounds and a chime when you win.", s.sounds) { vm.updateSettings(s.copy(sounds = it)) }
            Toggle("Gameplay animations", "Cards glide into place and turn over.", s.animations) { vm.updateSettings(s.copy(animations = it)) }
            Toggle("Win animation", "Cards bounce off the table when you win.", s.winAnimation, enabled = s.animations) { vm.updateSettings(s.copy(winAnimation = it)) }
        }
        Section("Game") {
            Choice(
                "Draw", listOf("Draw 1" to 1, "Draw 3" to 3), s.draw,
                detail = "Starts with your next game.",
            ) { vm.updateSettings(s.copy(draw = it)) }
            Toggle("Left-handed", "Stock and waste on the left, foundations on the right.", s.leftHanded) { vm.updateSettings(s.copy(leftHanded = it)) }
        }
        Section("Table") {
            PickerLabel("Background", backgrounds[s.background.coerceIn(backgrounds.indices)].name)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                backgrounds.forEachIndexed { i, b ->
                    val sel = i == s.background
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                            .background(tableBrush(b.palette))
                            .border(if (sel) 3.dp else 1.dp, if (sel) P.accent else P.text.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable { vm.updateSettings(s.copy(background = i)) },
                        contentAlignment = Alignment.Center,
                    ) { if (sel) Icon(Icons.check, null, tint = P.accent, modifier = Modifier.size(24.dp)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            PickerLabel("Card back", cardBacks[s.cardBack.coerceIn(cardBacks.indices)].name)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                cardBacks.forEachIndexed { i, b ->
                    val sel = i == s.cardBack
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .border(if (sel) 3.dp else 0.dp, if (sel) P.accent else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { vm.updateSettings(s.copy(cardBack = i)) }.padding(5.dp),
                        contentAlignment = Alignment.Center,
                    ) { CardBackArt(46.dp, b) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Section("Records") {
            Row(
                Modifier.fillMaxWidth().clickable { vm.go(Screen.STATS) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Statistics and top scores", color = P.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Icon(Icons.right, null, tint = P.textDim)
            }
        }
        Section("Scoring", "Windows standard scoring, like your old app.") {
            val rows = listOf(
                "Card from the waste to a column" to "+${Scoring.WASTE_TO_TABLEAU}",
                "Card up to a foundation" to "+${Scoring.TO_FOUNDATION}",
                "Turning over a column card" to "+${Scoring.TURN_OVER}",
                "Card back down from a foundation" to "${Scoring.FOUNDATION_TO_TABLEAU}",
                "Draw 1: turning the stock over" to "${Scoring.RECYCLE_DRAW1}",
                "Draw 3: each turn-over after the third" to "${Scoring.RECYCLE_DRAW3}",
                "Every 10 seconds" to "-${Scoring.TIME_PENALTY}",
            )
            for ((a, b) in rows) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
                Text(a, color = P.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(b, color = P.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Win bonus: 700,000 divided by your time in seconds, for wins over 30 seconds. Wins faster than 2:30 all get the 2:30 bonus (4666). " +
                    "The stock can be turned over as often as you like. Undo puts the cards and points back, but not the time.",
                color = P.textDim, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PickerLabel(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp)) {
        Text(label, color = P.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Text(value, color = P.textDim, fontSize = 16.sp)
    }
}

// ---------------------------------------------------------------- statistics

@Composable
fun StatsScreen(vm: GameViewModel) {
    val s = vm.stats
    ScreenScaffold("Statistics", vm::back) {
        Spacer(Modifier.height(8.dp))
        // Local headline: numbers with their labels underneath, which fits 360 dp at any font size.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.wash).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ring(if (s.played == 0) null else s.won / s.played.toFloat(), "${s.winRate}%", "won", size = 116.dp)
            Spacer(Modifier.width(20.dp))
            Column {
                Text("${s.won}", color = P.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, maxLines = 1)
                Text("games won", color = P.textDim, fontSize = 16.sp, maxLines = 1)
                Spacer(Modifier.height(10.dp))
                Text("${s.played}", color = P.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, maxLines = 1)
                Text("games played", color = P.textDim, fontSize = 16.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        TileRow(Tile(if (s.bestScore == 0) "–" else "${s.bestScore}", "best score"), Tile(if (s.leastMoves == 0) "–" else "${s.leastMoves}", "least moves"))
        TileRow(Tile(if (s.bestTime == 0L) "–" else clock(s.bestTime), "best time"), Tile("${s.streak}", if (s.streak == 1) "win in a row" else "wins in a row", highlight = s.streak >= 3))
        TileRow(Tile("${s.bestStreak}", "best streak"), Tile("${vm.daily.solved.size}", "dailies solved"))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(P.wash).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Total time", color = P.textDim, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(longClock(s.totalTime), color = P.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = Serif)
        }

        Section("Top scores") {
            val fmt = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)
            ScoreRow("Score", "Time", "Date", "Moves", header = true)
            if (s.top.isEmpty()) Text("Win a game to set a score.", color = P.textDim, fontSize = 16.sp, modifier = Modifier.padding(16.dp))
            for (e in s.top) ScoreRow("${e.score}", clock(e.seconds), runCatching { LocalDate.parse(e.date).format(fmt) }.getOrDefault(e.date), "${e.moves}")
        }

        if (s.recent.isNotEmpty()) Section("Recent games") {
            val fmt = java.text.SimpleDateFormat("MMM d", Locale.US)
            for (r in s.recent) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(if (r.won) P.accent else P.text.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) { Text(if (r.won) "W" else "L", color = if (r.won) P.onAccent else P.text, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (r.won) "Won · ${r.score} points" else "Gave up") + if (r.daily) " · daily" else "",
                        color = P.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text("${clock(r.seconds)} · ${r.moves} moves · ${fmt.format(java.util.Date(r.epochMs))}", color = P.textDim, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ConfirmTextButton("Reset statistics", "Tap again to erase all statistics") { vm.resetStats() }
        }
    }
}

@Composable
private fun ScoreRow(a: String, b: String, c: String, d: String, header: Boolean = false) {
    val color = if (header) P.textDim else P.text
    val w = if (header) FontWeight.SemiBold else FontWeight.Bold
    val size = if (header) fixedSp(16f) else fixedSp(18f)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (header) 6.dp else 7.dp)) {
        Text(a, color = color, fontSize = size, fontWeight = w, modifier = Modifier.weight(1f), maxLines = 1)
        Text(b, color = color, fontSize = size, fontWeight = w, modifier = Modifier.weight(0.9f), maxLines = 1)
        Text(c, color = color, fontSize = size, fontWeight = w, modifier = Modifier.weight(1.7f), maxLines = 1)
        Text(d, color = color, fontSize = size, fontWeight = w, modifier = Modifier.weight(0.9f), maxLines = 1, textAlign = TextAlign.End)
    }
}

// ---------------------------------------------------------------- daily challenges

private val BRONZE = Color(0xFFD08A5B)
private val SILVER = Color(0xFFC9D1DC)

@Composable
fun DailyScreen(vm: GameViewModel) {
    val today = LocalDate.now()
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthKey)
    var selKey by rememberSaveable { mutableStateOf(today.toString()) }
    val sel = LocalDate.parse(selKey)
    val days = month.lengthOfMonth()
    val solved = vm.daily.solvedIn(month.year, month.monthValue)

    ScreenScaffold("Daily challenges", vm::back) {
        // Trophies for 10, 20 and every day of the month.
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.wash).padding(16.dp)) {
            Text(
                "$solved of $days solved in ${month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.US)}",
                color = P.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            val steps = Daily.trophySteps + days
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                steps.forEachIndexed { i, n ->
                    val got = solved >= n
                    val c = listOf(BRONZE, SILVER, GOLD)[i]
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.trophy, null, tint = if (got) c else P.text.copy(alpha = 0.22f), modifier = Modifier.size(46.dp))
                        Text(if (i == 2) "All $n" else "$n days", color = if (got) P.text else P.textDim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(P.text.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(solved / days.toFloat()).height(10.dp).clip(RoundedCornerShape(50)).background(P.accent))
            }
        }

        // Calendar.
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.wash).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { monthKey = month.minusMonths(1).toString() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.left, "Previous month", tint = P.text)
                }
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)), color = P.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                val canNext = month.isBefore(YearMonth.from(today))
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(enabled = canNext) { monthKey = month.plusMonths(1).toString() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.right, "Next month", tint = if (canNext) P.text else P.text.copy(alpha = 0.2f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                for (n in listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")) {
                    Text(n, color = P.textDim, fontSize = fixedSp(14f), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            val lead = month.atDay(1).dayOfWeek.value % 7 // Sunday first
            val cells = lead + days
            for (row in 0 until (cells + 6) / 7) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0..6) {
                        val day = row * 7 + col - lead + 1
                        Box(Modifier.weight(1f).padding(3.dp).height(44.dp), contentAlignment = Alignment.Center) {
                            if (day in 1..days) {
                                val date = month.atDay(day)
                                val key = date.toString()
                                val future = date.isAfter(today)
                                val done = key in vm.daily.solved
                                val selected = key == selKey
                                val shape = RoundedCornerShape(10.dp)
                                Box(
                                    Modifier.fillMaxSize().clip(shape)
                                        .background(
                                            when {
                                                future -> Color.Transparent
                                                done -> P.accent
                                                else -> P.cardFace
                                            }
                                        )
                                        .border(if (selected) 3.dp else 0.dp, if (selected) Color(0xFF5AAEEA) else Color.Transparent, shape)
                                        .clickable(enabled = !future) { selKey = key },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$day", fontSize = fixedSp(17f), fontWeight = if (date == today) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = when { future -> P.text.copy(alpha = 0.35f); done -> P.onAccent; else -> P.ink },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected day.
        Spacer(Modifier.height(14.dp))
        val selDone = selKey in vm.daily.solved
        val inProgress = vm.game?.let { it.daily == selKey && !it.won } == true
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.wash).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    sel.format(DateTimeFormatter.ofPattern("MMM d", Locale.US)) + if (sel == today) " · Today" else "",
                    color = P.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
                Text(
                    when { selDone -> "Solved"; inProgress -> "In progress"; else -> "Not solved yet" },
                    color = if (selDone) P.accent else P.textDim, fontSize = 16.sp,
                )
            }
            PrimaryButton(
                when { vm.preparing != null -> "Dealing…"; inProgress -> "Continue"; selDone -> "Play again"; else -> "Play" },
                enabled = vm.preparing == null,
            ) { vm.playDaily(selKey) }
        }
        Text(
            "One deal a day, Draw 1. Every daily deal has been checked by a solver, so it can always be won.",
            color = P.textDim, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
        )
    }
}
