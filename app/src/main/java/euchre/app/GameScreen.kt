package euchre.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import euchre.core.Action
import euchre.core.Card
import euchre.core.GameState
import euchre.core.Phase
import euchre.core.Played
import euchre.core.ResultKind
import euchre.core.Suit
import euchre.core.sortHand
import euchre.core.team
import kotlin.math.abs
import kotlin.math.min

// ---------------------------------------------------------------- names

fun GameState.name(seat: Int): String =
    if (players == 4) listOf("You", "Left", "Partner", "Right")[seat] else listOf("You", "Left", "Right")[seat]

fun GameState.teamName(team: Int): String = if (players == 4) listOf("Us", "Them")[team] else name(team)

/** "You take" / "Partner takes". */
fun GameState.verb(seat: Int, base: String): String = if (seat == 0) "You $base" else "${name(seat)} ${base}s"

fun Suit?.trumpName(): String = this?.label ?: "No trump"

/** "Ace of Spades": easier to read in a sentence than a symbol. */
val Card.spoken: String
    get() = if (isJoker) "Joker" else {
        val r = when (rank) {
            euchre.core.Rank.ACE -> "Ace"; euchre.core.Rank.KING -> "King"; euchre.core.Rank.QUEEN -> "Queen"
            euchre.core.Rank.JACK -> "Jack"; else -> rank.short
        }
        "$r of ${suit.label}"
    }

fun Action.describe(): String = when (this) {
    is Action.Play -> "play the ${card.spoken}"
    is Action.Discard -> "discard the ${card.spoken}"
    Action.Pass -> "pass"
    is Action.OrderUp -> if (alone) "order it up and go alone" else "order it up"
    is Action.Call -> "call ${trump.trumpName()}" + if (alone) ", alone" else ""
    is Action.DefendAlone -> if (yes) "defend alone" else "don't defend alone"
    is Action.Misdeal -> if (yes) "call a redeal" else "keep the hand"
    is Action.GoUnder -> if (yes) "go under" else "keep the hand"
    is Action.NameSuit -> "name ${suit.trumpName()}"
    Action.NextHand -> "deal the next hand"
}

private val feltBrush = Brush.radialGradient(
    0f to Palette.feltLight, 0.55f to Palette.felt, 1f to Palette.feltDark,
    center = Offset(540f, 900f), radius = 1500f,
)

// ---------------------------------------------------------------- screen

@Composable
fun GameScreen(vm: GameViewModel) {
    val g = vm.game ?: return
    Box(Modifier.fillMaxSize().background(feltBrush)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            TopBar(g, vm)
            Box(Modifier.weight(1f).fillMaxWidth()) { Table(g, vm) }
            Prompt(g, vm)
            HandRow(g, vm)
        }
        val showResult = vm.shownTrick == null && (g.phase == Phase.HAND_OVER || g.phase == Phase.GAME_OVER)
        AnimatedVisibility(showResult, enter = fadeIn(tween(250)), exit = fadeOut(tween(150))) {
            ResultPanel(g, vm)
        }
    }
}

@Composable
private fun TopBar(g: GameState, vm: GameViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::leaveGame) { Text("‹ Menu", color = Palette.text) }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in 0 until g.rules.teams) ScorePill(g.teamName(t), g.scores[t], mine = t == g.rules.team(0))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm::requestHint, enabled = vm.humanTurn) {
                Text("Hint", color = if (vm.humanTurn) Palette.gold else Palette.textDim.copy(alpha = 0.3f))
            }
        }
        val c = g.contract
        Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (c != null && g.phase != Phase.HAND_OVER && g.phase != Phase.GAME_OVER) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color(0x40000000)).padding(horizontal = 14.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Trump ", color = Palette.textDim, fontSize = 15.sp)
                    if (c.trump != null) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(Palette.cream), contentAlignment = Alignment.Center) {
                            Text(c.trump!!.glyph, color = c.trump!!.color, fontSize = 17.sp)
                        }
                    } else Text("None", color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "   ${g.name(c.maker)} called" + if (c.alone) ", alone" else "",
                        color = Palette.textDim, fontSize = 15.sp,
                    )
                }
            } else {
                Text("Playing to ${g.rules.pointsToWin}", color = Palette.textDim, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ScorePill(label: String, score: Int, mine: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (mine) Palette.gold else Color(0x33000000))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (mine) Color(0xFF2A1F05) else Palette.text, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text("$score", color = if (mine) Color(0xFF2A1F05) else Palette.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------- table

private val prePlay = setOf(Phase.MISDEAL_CHECK, Phase.GO_UNDER, Phase.NAME_JOKER_SUIT, Phase.BID1, Phase.BID2, Phase.DISCARD, Phase.DEFEND_ALONE, Phase.LONER_DISCARD)

/** Unit direction from the table centre toward a seat. */
private fun seatDir(g: GameState, seat: Int): Pair<Float, Float> =
    if (g.players == 4) listOf(0f to 1f, -1f to 0f, 0f to -1f, 1f to 0f)[seat]
    else listOf(0f to 1f, -0.9f to -0.45f, 0.9f to -0.45f)[seat]

@Composable
private fun Table(g: GameState, vm: GameViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tableW = maxWidth
        val opponents = (1 until g.players).toList()
        for (seat in opponents) {
            val align = when {
                g.players == 4 && seat == 2 -> Alignment.TopCenter
                seat == 1 -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }
            val nudge = if (g.players == 3) Modifier.offset(y = (-40).dp) else Modifier
            Box(Modifier.align(align).then(nudge).padding(6.dp)) { Seat(g, vm, seat) }
        }
        Box(Modifier.align(Alignment.Center)) { Center(g, vm, tableW) }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)) { SeatTag(g, vm, 0) }
    }
}

@Composable
private fun Seat(g: GameState, vm: GameViewModel, seat: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val count = g.hands[seat].size
        Box(Modifier.size(width = 104.dp, height = 56.dp).alpha(if (g.isOut(seat)) 0.35f else 1f), contentAlignment = Alignment.Center) {
            for (i in 0 until count) {
                val k = i - (count - 1) / 2f
                CardBack(30.dp, Modifier.offset(x = (k * 11).dp, y = (abs(k) * 2).dp).rotate(k * 7f))
            }
        }
        Spacer(Modifier.height(4.dp))
        SeatTag(g, vm, seat)
    }
}

@Composable
private fun SeatTag(g: GameState, vm: GameViewModel, seat: Int) {
    val active = g.turn == seat && vm.shownTrick == null && g.phase != Phase.HAND_OVER && g.phase != Phase.GAME_OVER
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse",
    )
    val c = g.contract
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.clip(RoundedCornerShape(50))
                .background(if (active) Palette.gold.copy(alpha = pulse) else Color(0x40000000))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(g.name(seat), color = if (active) Color(0xFF2A1F05) else Palette.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (g.dealer == seat) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(20.dp).clip(CircleShape).background(Palette.cream), contentAlignment = Alignment.Center) {
                    Text("D", color = Palette.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (c != null && g.phase !in prePlay || g.phase == Phase.PLAY) {
            Spacer(Modifier.height(4.dp))
            when {
                g.isOut(seat) -> Text("Sitting out", color = Palette.textDim, fontSize = 13.sp)
                else -> TrickDots(g.tricksWon[seat])
            }
        }
        val bid = if (g.phase in prePlay) g.bids.lastOrNull { it.seat == seat }?.text else null
        AnimatedVisibility(bid != null, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
            Text(
                bid ?: "",
                Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(Palette.cream).padding(horizontal = 10.dp, vertical = 3.dp),
                color = Palette.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TrickDots(n: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(10.dp)) {
        repeat(n) { Box(Modifier.size(10.dp).clip(CircleShape).background(Palette.gold)) }
    }
}

@Composable
private fun Center(g: GameState, vm: GameViewModel, tableWidth: Dp) {
    val cardW = min(78f, tableWidth.value / 5.3f).dp
    val reach = cardW.value * 1.1f
    Box(Modifier.size(width = cardW * 4.2f, height = cardW * 3.6f), contentAlignment = Alignment.Center) {
        val st = vm.shownTrick
        when {
            g.phase in setOf(Phase.MISDEAL_CHECK, Phase.GO_UNDER, Phase.NAME_JOKER_SUIT, Phase.BID1) -> {
                Box {
                    CardBack(cardW, Modifier.offset(x = 4.dp, y = 4.dp))
                    PlayingCard(g.upcard!!, cardW)
                }
            }
            g.phase == Phase.BID2 -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box { CardBack(cardW, Modifier.offset(x = 4.dp, y = 4.dp)); CardBack(cardW) }
                    Spacer(Modifier.height(6.dp))
                    Text("${g.upcard!!.spoken} turned down", color = Palette.textDim, fontSize = 14.sp)
                }
            }
            st != null || g.trick.isNotEmpty() -> {
                val cards = st?.cards ?: g.trick
                for ((i, p) in cards.withIndex()) {
                    key(p) {
                        TrickCard(g, p, cardW, reach, z = i, winner = st?.winner, sweeping = st?.sweeping == true)
                    }
                }
            }
        }
    }
}

@Composable
private fun key(p: Played, content: @Composable () -> Unit) = androidx.compose.runtime.key(p.seat, p.card) { content() }

@Composable
private fun TrickCard(g: GameState, p: Played, cardW: Dp, reach: Float, z: Int, winner: Int?, sweeping: Boolean) {
    val density = LocalDensity.current
    val arrive = remember { Animatable(0f) }
    LaunchedEffect(Unit) { arrive.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
    val sweep by animateFloatAsState(if (sweeping) 1f else 0f, tween(360, easing = FastOutSlowInEasing), label = "sweep")
    val (dx, dy) = seatDir(g, p.seat)
    val far = 4.5f
    val (wx, wy) = winner?.let { seatDir(g, it) } ?: (0f to 0f)
    val tilt = listOf(-4f, 7f, 3f, -6f)[p.seat % 4]
    val t = arrive.value
    var x = dx * reach * (far + (1 - far) * t)
    var y = dy * reach * (far + (1 - far) * t)
    x += (wx * reach * far - x) * sweep
    y += (wy * reach * far - y) * sweep
    PlayingCard(
        p.card, cardW,
        Modifier
            .offset { with(density) { IntOffset(x.dp.roundToPx(), y.dp.roundToPx()) } }
            .graphicsLayer { rotationZ = tilt; alpha = 1f - sweep; shadowElevation = z.toFloat() },
        glow = winner == p.seat && !sweeping,
    )
}

// ---------------------------------------------------------------- prompt & actions

@Composable
private fun Prompt(g: GameState, vm: GameViewModel) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 118.dp).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(status(g, vm), color = Palette.text, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        vm.hint?.let {
            Text("Hint: ${it.describe()}", color = Palette.gold, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (vm.humanTurn) Actions(g, vm)
    }
}

private fun status(g: GameState, vm: GameViewModel): String {
    vm.shownTrick?.let { return g.verb(it.winner, "take") + " the trick" }
    if (g.phase == Phase.HAND_OVER || g.phase == Phase.GAME_OVER) return ""
    val pre = g.notice?.let { "$it\n" } ?: ""
    val c = g.contract
    if (g.turn != 0) {
        val who = g.name(g.turn)
        return pre + when (g.phase) {
            Phase.DISCARD -> "$who is picking up the ${g.upcard!!.spoken}"
            Phase.LONER_DISCARD -> "$who is taking partner's best card"
            Phase.PLAY -> "$who's turn"
            else -> "$who is deciding…"
        }
    }
    return pre + when (g.phase) {
        Phase.MISDEAL_CHECK -> "No ace, no face. Ask for a redeal?"
        Phase.GO_UNDER -> "You have three low cards. Go under and swap them for the kitty?"
        Phase.NAME_JOKER_SUIT -> "The Joker was turned up. Choose its suit."
        Phase.BID1 -> if (g.dealer == 0) "Pick up the ${g.upcard!!.spoken}? ${g.upSuit!!.label} would be trump."
            else "Order up the ${g.upcard!!.spoken}? ${g.upSuit!!.label} would be trump."
        Phase.BID2 -> if (Action.Pass !in g.legalActions()) "You're stuck as dealer. Name trump." else "Name trump, or pass."
        Phase.DISCARD -> "You picked up the ${g.upcard!!.spoken}. Tap a card to discard."
        Phase.LONER_DISCARD -> "Partner passed you the ${g.givenCard?.spoken}. Tap a card to discard."
        Phase.DEFEND_ALONE -> "${g.name(c!!.maker)} is going alone. Defend alone?"
        Phase.PLAY -> when {
            vm.selected != null -> "Tap the ${vm.selected?.spoken} again to play it."
            g.trick.isEmpty() -> "Your lead. Tap a card."
            else -> "Your turn. Tap a card."
        }
        else -> ""
    }
}

@Composable
private fun Actions(g: GameState, vm: GameViewModel) {
    val legal = g.legalActions()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        when (g.phase) {
            Phase.MISDEAL_CHECK -> {
                Secondary("Keep hand") { vm.act(Action.Misdeal(false)) }
                Primary("Redeal") { vm.act(Action.Misdeal(true)) }
            }
            Phase.GO_UNDER -> {
                Secondary("Keep hand") { vm.act(Action.GoUnder(false)) }
                Primary("Go under") { vm.act(Action.GoUnder(true)) }
            }
            Phase.DEFEND_ALONE -> {
                Secondary("No thanks") { vm.act(Action.DefendAlone(false)) }
                Primary("Defend alone") { vm.act(Action.DefendAlone(true)) }
            }
            Phase.NAME_JOKER_SUIT -> for (s in Suit.entries) SuitButton(s) { vm.act(Action.NameSuit(s)) }
            Phase.BID1 -> {
                Secondary("Pass") { vm.act(Action.Pass) }
                if (Action.OrderUp(false) in legal) Primary(if (g.dealer == 0) "Pick it up" else "Order it up") { vm.act(Action.OrderUp(false)) }
                if (Action.OrderUp(true) in legal) Secondary("Go alone") { vm.act(Action.OrderUp(true)) }
            }
            Phase.BID2 -> Bid2(g, vm, legal)
            else -> {}
        }
    }
}

@Composable
private fun Bid2(g: GameState, vm: GameViewModel, legal: List<Action>) {
    var alone by remember(g.handNo) { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (s in g.callableSuits()) {
                if (s == null) NoTrumpButton { vm.act(Action.Call(null, alone)) }
                else SuitButton(s) { vm.act(Action.Call(s, alone)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (g.rules.allowAlone) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { alone = !alone }) {
                    Switch(
                        alone, { alone = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.gold, checkedThumbColor = Palette.cream),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Go alone", color = Palette.text, fontSize = 17.sp)
                }
            }
            if (Action.Pass in legal) Secondary("Pass") { vm.act(Action.Pass) }
        }
    }
}

@Composable
private fun Primary(label: String, onClick: () -> Unit) {
    Button(
        onClick, shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.gold, contentColor = Color(0xFF2A1F05)),
        modifier = Modifier.heightIn(min = 52.dp),
    ) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp)) }
}

@Composable
private fun Secondary(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick, shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Palette.text.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text),
        modifier = Modifier.heightIn(min = 52.dp),
    ) { Text(label, fontSize = 18.sp) }
}

@Composable
private fun SuitButton(s: Suit, onClick: () -> Unit) {
    Box(
        Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(Palette.cream).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(s.glyph, color = s.color, fontSize = 34.sp) }
}

@Composable
private fun NoTrumpButton(onClick: () -> Unit) {
    Box(
        Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(Palette.cream).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("No\ntrump", color = Palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 15.sp) }
}

// ---------------------------------------------------------------- your hand

@Composable
private fun HandRow(g: GameState, vm: GameViewModel) {
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        val fullW = maxWidth
        val hand = sortHand(g.hands[0], g.trump)
        val n = hand.size.coerceAtLeast(1)
        val avail = maxWidth.value - 20f
        val cardW = min(84f, avail / (n - (n - 1) * 0.18f))
        val step = if (n == 1) 0f else min(cardW * 0.98f, (avail - cardW) / (n - 1))
        val cardH = cardW * 1.42f
        val legal = if (vm.humanTurn && g.phase == Phase.PLAY) g.legalActions().filterIsInstance<Action.Play>().map { it.card }.toSet() else null
        val discarding = vm.humanTurn && (g.phase == Phase.DISCARD || g.phase == Phase.LONER_DISCARD)
        val hinted = (vm.hint as? Action.Play)?.card ?: (vm.hint as? Action.Discard)?.card
        Box(Modifier.fillMaxWidth().height((cardH + 26).dp), contentAlignment = Alignment.BottomStart) {
            val start = (fullW.value - (cardW + step * (n - 1))) / 2f
            for ((i, card) in hand.withIndex()) {
                androidx.compose.runtime.key(card) {
                    val mid = (n - 1) / 2f
                    val k = i - mid
                    val playable = legal?.contains(card) == true || discarding
                    val raised = card == vm.selected || card == hinted
                    val lift by animateFloatAsState(if (raised) -22f else if (playable) -6f else 0f, label = "lift")
                    PlayingCard(
                        card, cardW.dp,
                        Modifier
                            .offset(x = (start + i * step).dp, y = (abs(k) * abs(k) * 2.2f + lift).dp)
                            .rotate(k * 3.2f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = null,
                                enabled = playable,
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                vm.tapCard(card)
                            },
                        dim = legal != null && !legal.contains(card),
                        glow = raised,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- end of hand / game

@Composable
private fun ResultPanel(g: GameState, vm: GameViewModel) {
    val r = g.result ?: return
    val us = g.rules.team(0)
    val makerTeam = g.rules.team(r.maker)
    val weCalled = makerTeam == us
    val made = r.makerTricks >= 3
    val gameOver = g.phase == Phase.GAME_OVER
    val title = when {
        gameOver && g.winner == us -> "You win!"
        gameOver -> "${g.teamName(g.winner!!)} ${if (g.players == 4) "win" else "wins"}"
        weCalled && r.kind == ResultKind.LONE_MARCH -> "Loner! All five tricks"
        weCalled && r.kind == ResultKind.MARCH -> "March! All five tricks"
        weCalled && made -> "Made it"
        weCalled -> "Euchred!"
        made && g.players == 4 -> if (r.kind == ResultKind.MADE) "They made it" else "They took all five"
        made -> "${g.name(r.maker)} made it"
        g.players == 4 -> "You euchred them!"
        else -> "${g.name(r.maker)} got euchred!"
    }
    val trump = g.contract?.trump.trumpName()
    val tricks = "${r.makerTricks} trick" + if (r.makerTricks == 1) "" else "s"
    val side = when {
        g.players != 4 || r.alone -> null
        weCalled -> "Your side"
        else -> "They"
    }
    val detail = "${g.name(r.maker)} called $trump" + (if (r.alone) " alone" else "") +
        if (side == null) " and took $tricks." else ". $side took $tricks."
    val gains = r.points.withIndex().filter { it.value > 0 }.joinToString("   ") { "+${it.value} ${g.teamName(it.index)}" }

    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp), color = Palette.panel,
            modifier = Modifier.padding(28.dp).widthIn(max = 420.dp).border(1.dp, Palette.panelLine, RoundedCornerShape(28.dp)),
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = Palette.gold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(detail, color = Palette.textDim, textAlign = TextAlign.Center, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(gains, color = Palette.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (t in 0 until g.rules.teams) ScorePill(g.teamName(t), g.scores[t], mine = t == us)
                }
                Spacer(Modifier.height(24.dp))
                if (gameOver) {
                    val s = vm.stats
                    Text("You've won ${s.won} of ${s.played} games", color = Palette.textDim, fontSize = 15.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Secondary("Menu") { vm.leaveGame() }
                        Primary("Play again") { vm.newGame() }
                    }
                } else {
                    Button(
                        { vm.act(Action.NextHand) }, shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.gold, contentColor = Color(0xFF2A1F05)),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Next hand", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
