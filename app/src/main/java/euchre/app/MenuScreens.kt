package euchre.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import euchre.core.BotProfile
import euchre.core.Card
import euchre.core.LoneLead
import euchre.core.Rank
import euchre.core.Rules
import euchre.core.Suit

private val menuBrush = Brush.verticalGradient(listOf(Palette.felt, Palette.feltDark))

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(vm: GameViewModel) {
    Box(Modifier.fillMaxSize().background(menuBrush).systemBarsPadding()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.height(170.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PlayingCard(Card(Rank.JACK, Suit.SPADES), 86.dp, Modifier.offset(x = (-46).dp).rotate(-14f))
                PlayingCard(Card(Rank.JACK, Suit.CLUBS), 86.dp, Modifier.offset(x = 46.dp).rotate(14f))
                PlayingCard(Card(Rank.ACE, Suit.HEARTS), 92.dp, Modifier.offset(y = (-6).dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Euchre", style = MaterialTheme.typography.headlineLarge, color = Palette.text)
            val s = vm.stats
            Text(
                if (s.played == 0) "Pull up a chair" else "You've won ${s.won} of ${s.played} games",
                color = Palette.textDim, fontSize = 17.sp,
            )
            Spacer(Modifier.height(44.dp))
            if (vm.canResume) {
                BigButton("Continue game", primary = true) { vm.resume() }
                Spacer(Modifier.height(14.dp))
                BigButton("New game", primary = false) { vm.newGame() }
            } else {
                BigButton("New game", primary = true) { vm.newGame() }
            }
            Spacer(Modifier.height(14.dp))
            BigButton("Settings", primary = false) { vm.screen = Screen.SETTINGS }
            Spacer(Modifier.height(6.dp))
            TextButton({ vm.screen = Screen.HELP }) { Text("How to play", color = Palette.textDim, fontSize = 17.sp) }
        }
    }
}

@Composable
private fun BigButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val m = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(60.dp)
    if (primary) {
        Button(onClick, m, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Palette.gold, contentColor = Color(0xFF2A1F05))) {
            Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick, m, shape = RoundedCornerShape(50),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Palette.text.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text),
        ) { Text(label, fontSize = 20.sp) }
    }
}

// ---------------------------------------------------------------- settings

@Composable
private fun ScreenScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(menuBrush).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onBack) { Text("‹ Back", color = Palette.text, fontSize = 18.sp) }
                Spacer(Modifier.weight(1f))
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.text)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(80.dp))
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 32.dp),
            ) { content() }
        }
    }
}

@Composable
private fun Section(title: String, note: String? = null, content: @Composable () -> Unit) {
    Spacer(Modifier.height(22.dp))
    Text(title.uppercase(), color = Palette.gold, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    if (note != null) Text(note, color = Palette.textDim, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0x33000000)).padding(vertical = 6.dp)) { content() }
}

@Composable
private fun Toggle(label: String, detail: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled) Palette.text else Palette.textDim.copy(alpha = 0.4f), fontSize = 18.sp)
            Text(detail, color = Palette.textDim.copy(alpha = if (enabled) 0.8f else 0.35f), fontSize = 14.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked, onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.gold, checkedThumbColor = Palette.cream),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choice(label: String, options: List<Pair<String, T>>, value: T, detail: String? = null, onChange: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, color = Palette.text, fontSize = 18.sp)
        if (detail != null) Text(detail, color = Palette.textDim.copy(alpha = 0.8f), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((name, v) in options) Chip(name, v == value) { onChange(v) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50))
            .background(if (selected) Palette.gold else Color(0x26FFFFFF))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) Color(0xFF2A1F05) else Palette.text, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
        RoundBtn("–", value > range.first) { onChange(value - 1) }
        Text("$value", color = Palette.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
        RoundBtn("+", value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun RoundBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Color(0x26FFFFFF)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) Palette.text else Palette.textDim.copy(alpha = 0.3f), fontSize = 22.sp) }
}

@Composable
private fun ProfileSliders(who: String, p: BotProfile, onChange: (BotProfile) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(who, color = Palette.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        LabeledSlider("Skill", BotProfile.iqLabel(p.iq), p.iq) { onChange(p.copy(iq = it)) }
        LabeledSlider("Boldness", BotProfile.aggressionLabel(p.aggression), p.aggression) { onChange(p.copy(aggression = it)) }
    }
}

@Composable
private fun LabeledSlider(label: String, valueLabel: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.textDim, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(valueLabel, color = Palette.gold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
    Slider(
        value.toFloat(), { onChange(it.toInt()) }, valueRange = 0f..100f,
        colors = SliderDefaults.colors(thumbColor = Palette.gold, activeTrackColor = Palette.gold, inactiveTrackColor = Color(0x33FFFFFF)),
    )
}

private data class Level(val name: String, val partner: BotProfile, val opponents: BotProfile)

private val levels = listOf(
    Level("Easy", BotProfile(50, 75), BotProfile(45, 20)),
    Level("Normal", BotProfile(50, 65), BotProfile(50, 55)),
    Level("Hard", BotProfile(50, 90), BotProfile(55, 90)),
)

@Composable
fun SettingsScreen(vm: GameViewModel) {
    val s = vm.settings
    val r = s.rules
    fun setRules(nr: Rules) = vm.updateSettings(s.copy(rules = nr.normalized()))
    val cutthroat = r.players == 3

    ScreenScaffold("Settings", { vm.screen = Screen.HOME }) {
        Section("Computer players", "Pick a level, or fine-tune each side below.") {
            Choice("Level", levels.map { it.name to it.name } + ("Custom" to "Custom"),
                levels.firstOrNull { it.partner == s.partner && it.opponents == s.opponents }?.name ?: "Custom",
            ) { name -> levels.firstOrNull { it.name == name }?.let { vm.updateSettings(s.copy(partner = it.partner, opponents = it.opponents)) } }
            if (!cutthroat) ProfileSliders("Your partner", s.partner) { vm.updateSettings(s.copy(partner = it)) }
            ProfileSliders("Opponents", s.opponents) { vm.updateSettings(s.copy(opponents = it)) }
        }

        Section("Table") {
            Choice("Speed", listOf("Relaxed" to 0, "Normal" to 1, "Quick" to 2), s.speed) { vm.updateSettings(s.copy(speed = it)) }
            Toggle("Play forced cards for me", "When only one card can be played, play it automatically.", s.autoPlaySingle) { vm.updateSettings(s.copy(autoPlaySingle = it)) }
            Toggle("Tap twice to play", "First tap lifts the card, second tap plays it.", s.tapTwice) { vm.updateSettings(s.copy(tapTwice = it)) }
        }

        Text(
            "Rule changes take effect when you start a new game.",
            color = Palette.textDim, fontSize = 14.sp, modifier = Modifier.padding(top = 22.dp),
        )

        Section("House rules", "Start from a common set of rules.") {
            Choice("Rule set", Rules.presets.map { it.first to it.first } + ("Custom" to "Custom"),
                Rules.presets.firstOrNull { it.second.normalized() == r }?.first ?: "Custom",
            ) { name -> Rules.presets.firstOrNull { it.first == name }?.let { setRules(it.second) } }
        }

        Section("The game") {
            Choice("Players", listOf("4 (partners)" to 4, "3 (cutthroat)" to 3), r.players) { setRules(r.copy(players = it, allowAlone = it == 4 || r.allowAlone)) }
            Choice("Play to", listOf(5, 7, 10, 11, 15).map { "$it" to it }, r.pointsToWin) { setRules(r.copy(pointsToWin = it)) }
            Choice("Deck", listOf("24 cards (9–A)" to Rank.NINE, "28 (8–A)" to Rank.EIGHT, "32 (7–A)" to Rank.SEVEN), r.deckLow) { setRules(r.copy(deckLow = it)) }
            Toggle("Joker (Benny)", "Adds a joker as the highest trump of all.", r.joker) { setRules(r.copy(joker = it)) }
        }

        Section("Bidding") {
            Toggle("Stick the dealer", "If everyone passes twice, the dealer must name trump. Off: the hand is thrown in and redealt.", r.stickTheDealer) { setRules(r.copy(stickTheDealer = it)) }
            Toggle("Going under", "A player with three 9s or 10s may swap them for the three kitty cards before bidding.", r.goingUnder) { setRules(r.copy(goingUnder = it)) }
            Toggle("No ace, no face", "A hand with no ace or face card may ask for a redeal.", r.noAceNoFace) { setRules(r.copy(noAceNoFace = it)) }
            Toggle("Call the turned-down suit", "Allow naming the suit that was turned down in round two.", r.callTurnedDownSuit) { setRules(r.copy(callTurnedDownSuit = it)) }
            Toggle("No trump", "Allow calling no trump in round two. Not available with the joker.", r.noTrump, enabled = !r.joker) { setRules(r.copy(noTrump = it)) }
        }

        Section("Going alone") {
            Toggle("Allow going alone", "The caller may play without their partner for extra points.", r.allowAlone, enabled = !cutthroat) { setRules(r.copy(allowAlone = it)) }
            val alone = r.allowAlone && !cutthroat
            Toggle("Defend alone", "An opponent may also go alone against a loner.", r.defendAlone, enabled = alone) { setRules(r.copy(defendAlone = it)) }
            Toggle("Canadian loner", "If the dealer's partner orders it up, they must go alone.", r.canadianLoner, enabled = alone) { setRules(r.copy(canadianLoner = it)) }
            Toggle("Partner's best card", "A loner swaps one card for their partner's best.", r.partnerBestCard, enabled = alone) { setRules(r.copy(partnerBestCard = it)) }
            if (alone) Choice("Who leads against a loner", listOf("Left of dealer" to LoneLead.LEFT_OF_DEALER, "Left of loner" to LoneLead.LEFT_OF_LONER), r.loneLead) { setRules(r.copy(loneLead = it)) }
        }

        Section("Scoring") {
            Stepper("Making it (3–4 tricks)", r.makerPoints, 1..3) { setRules(r.copy(makerPoints = it)) }
            Stepper("March (all 5)", r.marchPoints, 1..4) { setRules(r.copy(marchPoints = it)) }
            if (!cutthroat) Stepper("Loner march", r.loneMarchPoints, 2..8) { setRules(r.copy(loneMarchPoints = it)) }
            Stepper("Euchre", r.euchrePoints, 1..4) { setRules(r.copy(euchrePoints = it)) }
            if (r.defendAlone && !cutthroat) Stepper("Euchre by a lone defender", r.defendAloneEuchrePoints, 2..8) { setRules(r.copy(defendAloneEuchrePoints = it)) }
            Toggle("Super euchre", "Defenders who take all five tricks score extra.", r.superEuchre) { setRules(r.copy(superEuchre = it)) }
            if (r.superEuchre) Stepper("Super euchre points", r.superEuchrePoints, 2..8) { setRules(r.copy(superEuchrePoints = it)) }
        }

        Section("Your record") {
            val st = vm.stats
            StatLine("Games won", "${st.won} of ${st.played}")
            StatLine("Best winning streak", "${st.bestStreak}")
            StatLine("Hands you called and made", "${st.made} of ${st.called}")
            StatLine("Times your side euchred them", "${st.euchres}")
            StatLine("Lone marches", "${st.loners}")
            TextButton(vm::resetStats, Modifier.padding(horizontal = 6.dp)) { Text("Reset record", color = Palette.textDim) }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, color = Palette.textDim, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(value, color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- help

@Composable
fun HelpScreen(vm: GameViewModel) {
    ScreenScaffold("How to play", { vm.screen = Screen.HOME }) {
        val paras = listOf(
            "The idea" to "You and your partner (across the table) try to win at least three of the five tricks in each hand. First side to ${vm.settings.rules.pointsToWin} points wins.",
            "Picking trump" to "One card is turned up. Going around the table, each player can tell the dealer to pick it up, making that suit trump, or pass. If everyone passes, the card is turned down and each player may name any other suit, or pass again.",
            "The bowers" to "The jack of trump (the right bower) is the highest card. The other jack of the same colour (the left bower) is second highest and counts as trump. Then ace, king, queen, ten, nine.",
            "Playing" to "You must follow the suit that was led if you can. If you can't, play anything, including trump. Highest trump wins the trick; if no trump was played, the highest card of the suit led wins.",
            "Scoring" to "The side that named trump scores 1 point for 3 or 4 tricks and 2 for all five. If they take fewer than three, they're euchred and the other side scores 2.",
            "Going alone" to "If your hand is very strong, go alone: your partner sits out, and taking all five tricks scores 4 points.",
            "Tips" to "Tap Hint at the top of the table to see what a strong player would do. The lit-up name shows whose turn it is, and \"D\" marks the dealer.",
        )
        for ((h, body) in paras) {
            Spacer(Modifier.height(20.dp))
            Text(h, color = Palette.gold, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = Palette.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
