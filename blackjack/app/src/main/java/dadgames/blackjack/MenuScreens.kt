package dadgames.blackjack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dadgames.blackjack.core.DoubleRule
import dadgames.blackjack.core.Rules
import dadgames.blackjack.core.TABLES
import dadgames.cards.Card
import dadgames.cards.Rank
import dadgames.cards.Suit
import dadgames.ui.BigButton
import dadgames.ui.Choice
import dadgames.ui.ConfirmTextButton
import dadgames.ui.P
import dadgames.ui.PlayingCard
import dadgames.ui.PrimaryButton
import dadgames.ui.ScreenScaffold
import dadgames.ui.Section
import dadgames.ui.Serif
import dadgames.ui.StatsHeadline
import dadgames.ui.Tile
import dadgames.ui.TileRow
import dadgames.ui.Toggle
import dadgames.ui.menuBackground
import kotlinx.coroutines.launch

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
                PlayingCard(Card(Rank.ACE, Suit.SPADES), 92.dp, Modifier.offset(x = (-34).dp).rotate(-10f))
                PlayingCard(Card(Rank.KING, Suit.HEARTS), 92.dp, Modifier.offset(x = 34.dp, y = 6.dp).rotate(10f))
            }
            Spacer(Modifier.height(10.dp))
            Text("Blackjack", style = MaterialTheme.typography.headlineLarge, color = P.text)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(vm.profile.level, vm.profile.xp)
                Spacer(Modifier.width(12.dp))
                BalancePill(vm.game.balance)
            }
            Spacer(Modifier.height(10.dp))
            val s = vm.stats
            Text(
                when {
                    s.hands == 0L -> "Your table is ready"
                    s.streak >= 2 -> "${s.streak} wins in a row · ${s.winRate}% won"
                    else -> "${s.hands.commas()} hands · ${s.winRate}% won"
                },
                color = P.textDim, fontSize = 17.sp,
            )
            Spacer(Modifier.height(30.dp))
            BigButton(if (vm.midHand) "Continue hand" else "Play at ${vm.table.name}", primary = true) { vm.openTable() }
            Spacer(Modifier.height(14.dp))
            BigButton("Tables", primary = false) { vm.screen = Screen.TABLES }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.widthIn(max = 360.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { BigButton("Stats", primary = false) { vm.screen = Screen.STATS } }
                Box(Modifier.weight(1f)) { BigButton("Settings", primary = false) { vm.screen = Screen.SETTINGS } }
            }
            Spacer(Modifier.height(6.dp))
            TextButton({ vm.screen = Screen.HELP }) { Text("How to play", color = P.textDim, fontSize = 17.sp) }
        }
    }
}

// ---------------------------------------------------------------- tables

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TablesScreen(vm: GameViewModel) {
    val pager = rememberPagerState(initialPage = vm.profile.table) { TABLES.size }
    val scope = rememberCoroutineScope()
    ScreenScaffold("Tables", onBack = { if (vm.midHand) vm.openTable() else vm.screen = Screen.HOME }) {
        Spacer(Modifier.height(8.dp))
        Text(
            if (vm.midHand) "Finish the hand you're playing before changing tables."
            else "Same rules at every table. Swipe for bigger stakes.",
            color = P.textDim, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        HorizontalPager(pager, Modifier.fillMaxWidth().height(470.dp), pageSpacing = 14.dp) { i ->
            val t = TABLES[i]
            val here = i == vm.profile.table
            Column(
                Modifier.fillMaxSize().padding(horizontal = 18.dp).shadow(10.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(Color(t.felt), Color(t.feltDark))))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(t.name, color = Color.White, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 36.sp, maxLines = 1)
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.92f)).padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Table bets", color = Color(0xFF3A4A5A), fontSize = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Min ", color = Color(0xFF1C2A3A), fontSize = 22.sp)
                        Text(t.minBet.short(), color = Color(0xFF1C2A3A), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("    Max ", color = Color(0xFF1C2A3A), fontSize = 22.sp)
                        Text(t.maxBet.short(), color = Color(0xFF1C2A3A), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Box(Modifier.height(130.dp), contentAlignment = Alignment.Center) {
                    val chips = dadgames.blackjack.core.CHIPS.filter { it <= t.maxBet && it >= t.minBet }.takeLast(3)
                    for ((k, v) in chips.withIndex()) Chip(v, 72.dp, Modifier.offset(x = ((k - (chips.size - 1) / 2f) * 62).dp))
                }
                Text(vm.settings.rules.dealerLine, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, maxLines = 1)
                Text("Blackjack pays 3 to 2", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, maxLines = 1)
                Spacer(Modifier.weight(1f))
                val enabled = !vm.midHand || here
                PrimaryButton(
                    if (here && vm.midHand) "Back to the hand" else if (here) "Play here" else "Play",
                    Modifier.fillMaxWidth(), enabled = enabled,
                ) { if (vm.midHand) vm.openTable() else vm.selectTable(i) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            for (i in TABLES.indices) {
                val sel = pager.currentPage == i
                Box(
                    Modifier.padding(6.dp).size(if (sel) 16.dp else 11.dp).clip(CircleShape)
                        .background(if (sel) P.text else P.text.copy(alpha = 0.35f))
                        .clickable { scope.launch { pager.animateScrollToPage(i) } },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- settings

private data class Preset(val name: String, val rules: Rules)

private val PRESETS = listOf(
    Preset("Your usual", Rules()),
    Preset("Atlantic City", Rules(decks = 8, lateSurrender = true)),
    Preset("Downtown Vegas", Rules(decks = 2, dealerHitsSoft17 = true)),
)

@Composable
fun SettingsScreen(vm: GameViewModel) {
    val s = vm.settings
    val r = s.rules
    fun setRules(nr: Rules) = vm.updateSettings(s.copy(rules = nr))
    ScreenScaffold("Settings", onBack = { vm.screen = Screen.HOME }) {
        Section("Play") {
            Toggle("Sound effects", "Card, chip and win sounds.", s.sound) { vm.updateSettings(s.copy(sound = it)) }
            Toggle("Hint button", "The light bulb shows the best move for your hand.", s.hintButton) { vm.updateSettings(s.copy(hintButton = it)) }
            Toggle("Auto hint", "Always light up the best move's button.", s.autoHint) { vm.updateSettings(s.copy(autoHint = it)) }
            Choice("Deal speed", listOf("Relaxed" to 0, "Normal" to 1, "Quick" to 2), s.speed) { vm.updateSettings(s.copy(speed = it)) }
        }
        Section("Table rules", note = "Changes start with the next hand.") {
            val preset = PRESETS.firstOrNull { it.rules == r }?.name
            Choice("Rule set", PRESETS.map { it.name to it.name }, preset ?: "", detail = if (preset == null) "Custom rules" else null) { n ->
                setRules(PRESETS.first { it.name == n }.rules)
            }
            Choice("Decks in the shoe", listOf("2" to 2, "4" to 4, "6" to 6, "8" to 8), r.decks) { setRules(r.copy(decks = it)) }
            Toggle("Dealer hits soft 17", "Off: dealer stands on soft 17 (better for you).", r.dealerHitsSoft17) { setRules(r.copy(dealerHitsSoft17 = it)) }
            Toggle("Insurance", "Offer insurance when the dealer shows an ace.", r.insurance) { setRules(r.copy(insurance = it)) }
            Toggle("Surrender", "Give up half your bet on your first two cards.", r.lateSurrender) { setRules(r.copy(lateSurrender = it)) }
            Choice("Double down on", DoubleRule.entries.map { it.label to it }, r.doubleRule) { setRules(r.copy(doubleRule = it)) }
            Toggle("Double after split", "You may double a hand you got by splitting.", r.doubleAfterSplit) { setRules(r.copy(doubleAfterSplit = it)) }
            Choice("Split up to", listOf("2 hands" to 2, "3 hands" to 3, "4 hands" to 4), r.maxHands) { setRules(r.copy(maxHands = it)) }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Blackjack pays 3 to 2. Split aces get one card each. The dealer checks for blackjack with an ace or ten showing.",
            color = P.textDim, fontSize = 14.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text("Version 1.0.0", color = P.textDim, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- stats

@Composable
fun StatsScreen(vm: GameViewModel) {
    val s = vm.stats
    ScreenScaffold("Stats", onBack = { vm.screen = Screen.HOME }) {
        Spacer(Modifier.height(8.dp))
        StatsHeadline(
            if (s.hands == 0L) null else s.won.toFloat() / s.hands, "${s.winRate}%", "hands won",
            s.hands.commas() to "hands played", s.biggestWin.short() to "biggest win",
        )
        Section("Balance") {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(vm.game.balance.commas(), color = P.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = Serif)
                    Spacer(Modifier.width(8.dp))
                    Text("Level ${vm.profile.level}", color = P.textDim, fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text("Balance over the last ${s.history.size - 1} hands", color = P.textDim, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                if (s.history.size >= 3) BalanceChart(s.history)
                else Text("Play a few hands to see the chart.", color = P.textDim, fontSize = 15.sp, modifier = Modifier.padding(vertical = 18.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        TileRow(Tile(s.won.commas(), "won"), Tile(s.lost.commas(), "lost"))
        TileRow(Tile(s.pushed.commas(), "pushed"), Tile(s.blackjacks.commas(), "blackjacks"))
        TileRow(Tile("${s.doublesWon.commas()} of ${s.doubles.commas()}", "doubles won"), Tile(s.splits.commas(), "splits"))
        TileRow(Tile(s.biggestBet.short(), "biggest bet"), Tile(s.bestBalance.short(), "best balance"))
        TileRow(
            Tile(
                when {
                    s.streak > 0 -> "${s.streak} won"
                    s.streak < 0 -> "${-s.streak} lost"
                    else -> "-"
                },
                "current streak", highlight = s.streak >= 3,
            ),
            Tile("${s.bestStreak}", "best win streak"),
        )
        if (s.recent.isNotEmpty()) {
            Section("Recent hands") {
                for (h in s.recent.take(15)) RecentRow(h)
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ConfirmTextButton("Reset stats", "Tap again to reset (chips and level stay)") { vm.resetStats() }
        }
    }
}

@Composable
private fun RecentRow(h: HandRecord) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(h.result, color = P.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("You ${h.player} · Dealer ${h.dealer} · bet ${h.staked.short()}", color = P.textDim, fontSize = 14.sp, maxLines = 1)
        }
        Text(
            if (h.net == 0L) "0" else h.net.signed(),
            color = when {
                h.net > 0 -> Color(0xFF7BE08F)
                h.net < 0 -> Color(0xFFFF9A8F)
                else -> P.textDim
            },
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** One series, one hue: thin line, recessive grid, end value labelled, drag for a crosshair. */
@Composable
private fun BalanceChart(points: List<Long>) {
    val line = P.accent
    val grid = P.text.copy(alpha = 0.12f)
    val ink = P.textDim
    var pick by remember(points) { mutableStateOf<Int?>(null) }
    val lo = points.min()
    val hi = points.max()
    Column {
        Box(Modifier.fillMaxWidth().height(24.dp)) {
            val i = pick
            if (i != null) Text(
                "Hand ${i + 1 - points.size}: ${points[i].commas()}".replace("Hand 0:", "Now:"),
                color = P.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            ) else Text("Drag across the chart to see a hand", color = ink, fontSize = 14.sp)
        }
        Row {
            Column(Modifier.height(150.dp).width(58.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(hi.short(), color = ink, fontSize = 13.sp)
                Text(lo.short(), color = ink, fontSize = 13.sp)
            }
            Canvas(
                Modifier.weight(1f).height(150.dp)
                    .pointerInput(points) {
                        fun idx(x: Float) = ((x / size.width) * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
                        detectDragGestures(onDragEnd = { pick = null }, onDragCancel = { pick = null }) { ch, _ -> pick = idx(ch.position.x) }
                    }
                    .pointerInput(points) {
                        detectTapGestures { pick = ((it.x / size.width) * (points.size - 1)).toInt().coerceIn(0, points.size - 1) }
                    },
            ) {
                val span = (hi - lo).toDouble().takeIf { it > 0 } ?: 1.0
                fun px(i: Int) = size.width * i / (points.size - 1).toFloat()
                fun py(v: Long) = (size.height * (1 - (v - lo) / span)).toFloat().coerceIn(2f, size.height - 2f)
                for (k in 0..2) {
                    val y = size.height * k / 2f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                val path = Path()
                for ((i, v) in points.withIndex()) if (i == 0) path.moveTo(px(i), py(v)) else path.lineTo(px(i), py(v))
                drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(line, 4.dp.toPx(), Offset(px(points.size - 1), py(points.last())))
                pick?.let { i ->
                    drawLine(ink, Offset(px(i), 0f), Offset(px(i), size.height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                    drawCircle(Color.White, 5.dp.toPx(), Offset(px(i), py(points[i])))
                    drawCircle(line, 3.5.dp.toPx(), Offset(px(i), py(points[i])))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- help

@Composable
fun HelpScreen(vm: GameViewModel) {
    ScreenScaffold("How to play", onBack = { vm.screen = Screen.HOME }) {
        val paras = listOf(
            "Goal" to "Get closer to 21 than the dealer without going over. Number cards count their number, faces count 10, an ace counts 1 or 11. A soft hand has an ace counted as 11; it shows two totals, like 7 / 17.",
            "Betting" to "Tap chips to build your bet, then tap Deal. Your bet stays for the next hand. Hold Deal to deal the same bet every hand automatically; tap the gold Auto deal sign to stop.",
            "Your turn" to "Hit takes a card. Stand keeps your total. Double doubles your bet for exactly one more card. Split turns a pair into two hands, each with its own bet (up to four hands). Split aces get one card each.",
            "The dealer" to "The dealer checks for blackjack when showing an ace or a ten. After you stand, the dealer draws to 17 and stands (or hits soft 17 if you turn that rule on).",
            "Payouts" to "Blackjack pays 3 to 2. A win pays even money and a tie (push) returns your bet. Insurance, when switched on, pays 2 to 1. Surrender, when switched on, returns half your bet.",
            "Hints" to "Tap the light bulb for the basic-strategy move, worked out for the rules you're playing. Auto hint lights up the right button every time.",
            "Chips" to "Play money only. If you run out, you get free chips. Nothing is for sale.",
        )
        for ((h, b) in paras) {
            Spacer(Modifier.height(18.dp))
            Text(h, color = P.accent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(b, color = P.text, fontSize = 17.sp, lineHeight = 25.sp)
        }
    }
}
