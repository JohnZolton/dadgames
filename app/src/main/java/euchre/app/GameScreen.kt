package euchre.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
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

/** Drops the font's top/bottom padding so a single glyph sits dead centre in a circle. */
private val centeredGlyph = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    lineHeight = 1.em,
)

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
                            Text(c.trump!!.glyph, color = c.trump!!.color, fontSize = 17.sp, style = centeredGlyph)
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
            // Side seats sit above the horizontal trick cards so nothing overlaps.
            val nudge = when {
                g.players == 4 && seat == 2 -> Modifier
                g.players == 4 -> Modifier.offset(y = (-64).dp)
                else -> Modifier.offset(y = (-100).dp)
            }
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
        0f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse",
    )
    val c = g.contract
    val tagShape = RoundedCornerShape(50)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .graphicsLayer { if (active) { scaleX = 1.06f + 0.06f * pulse; scaleY = scaleX } }
                .shadow(if (active) 14.dp else 0.dp, tagShape, ambientColor = Palette.gold, spotColor = Palette.gold)
                .clip(tagShape)
                .background(if (active) Palette.gold else Color(0x40000000))
                .border(if (active) 2.dp else 0.dp, if (active) Palette.cream.copy(alpha = 0.5f + 0.5f * pulse) else Color.Transparent, tagShape)
                .padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(g.name(seat), color = if (active) Color(0xFF2A1F05) else Palette.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (g.dealer == seat) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(20.dp).clip(CircleShape).background(Palette.cream), contentAlignment = Alignment.Center) {
                    Text("D", color = Palette.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = centeredGlyph)
                }
            }
        }
        if (active && seat != 0) ThinkingDots()
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
private fun ThinkingDots() {
    val t by rememberInfiniteTransition(label = "dots").animateFloat(
        0f, 3f, infiniteRepeatable(tween(1100, easing = androidx.compose.animation.core.LinearEasing)), label = "dots",
    )
    Row(Modifier.padding(top = 5.dp).height(10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val on = ((t - i + 3f) % 3f) < 1f
            Box(Modifier.size(if (on) 9.dp else 7.dp).clip(CircleShape).background(if (on) Palette.gold else Palette.text.copy(alpha = 0.35f)))
        }
    }
}

/** Gold arrow pointing from the table centre toward whoever's turn it is. */
@Composable
private fun TurnArrow(g: GameState, seat: Int, distance: Float) {
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        0f, 1f, infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
    )
    val (dx, dy) = seatDir(g, seat)
    val angle by animateFloatAsState(Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat(), tween(300), label = "angle")
    val density = LocalDensity.current
    val d = distance + 7f * bob
    androidx.compose.foundation.Canvas(
        Modifier
            .offset { with(density) { IntOffset((dx * d).dp.roundToPx(), (dy * d).dp.roundToPx()) } }
            .size(34.dp)
            .zIndex(10f)
            .graphicsLayer { rotationZ = angle },
    ) {
        val w = size.width
        val h = size.height
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.92f, h / 2); lineTo(w * 0.18f, h * 0.12f); lineTo(w * 0.36f, h / 2); lineTo(w * 0.18f, h * 0.88f); close()
        }
        drawPath(p, Color(0x66000000), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.14f))
        drawPath(p, Palette.gold)
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
    // Leave room for the side seats: ~110dp each.
    val cardW = min(66f, (tableWidth.value - 230f) / 2.5f).coerceAtLeast(50f).dp
    val reach = cardW.value * 0.95f
    val st0 = vm.shownTrick
    val turnSeat = if (st0 == null && g.phase != Phase.HAND_OVER && g.phase != Phase.GAME_OVER) g.turn else null
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
        // In play the arrow sits in the gap between trick cards; during bidding it clears the up card.
        if (turnSeat != null) TurnArrow(g, turnSeat, if (g.phase == Phase.PLAY) 4f else cardW.value * 1.05f)
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
        val yours = vm.humanTurn
        Text(
            status(g, vm), color = if (yours) Palette.gold else Palette.text,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            fontSize = if (yours) 22.sp else 19.sp, fontWeight = if (yours) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
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
            g.trick.isEmpty() -> "Your lead. Tap or swipe up a card."
            else -> "Your turn. Tap or swipe up a card."
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
                // Widths roughly follow label length so all three fit on one line on a phone.
                Secondary("Pass", Modifier.weight(0.8f)) { vm.act(Action.Pass) }
                if (Action.OrderUp(false) in legal) Primary(if (g.dealer == 0) "Pick it up" else "Order it up", Modifier.weight(1.3f)) { vm.act(Action.OrderUp(false)) }
                if (Action.OrderUp(true) in legal) Secondary("Go alone", Modifier.weight(1.1f)) { vm.act(Action.OrderUp(true)) }
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

private val pillPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)

@Composable
private fun Primary(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick, shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.gold, contentColor = Color(0xFF2A1F05)),
        contentPadding = pillPadding,
        modifier = modifier.heightIn(min = 52.dp),
    ) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
}

@Composable
private fun Secondary(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick, shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Palette.text.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text),
        contentPadding = pillPadding,
        modifier = modifier.heightIn(min = 52.dp),
    ) { Text(label, fontSize = 18.sp, maxLines = 1, softWrap = false) }
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
                    // Swipe up to play: the card follows the finger and plays past the threshold.
                    val drag = remember { Animatable(0f) }
                    val scope = rememberCoroutineScope()
                    val density = LocalDensity.current.density
                    val threshold = maxOf(50f, cardH * 0.45f)
                    PlayingCard(
                        card, cardW.dp,
                        Modifier
                            .offset(x = (start + i * step).dp, y = (abs(k) * abs(k) * 2.2f + lift + drag.value).dp)
                            .rotate(k * 3.2f * (1f + drag.value / threshold).coerceIn(0f, 1f))
                            .zIndex(if (drag.value < 0f) 1f else 0f)
                            .pointerInput(card, playable) {
                                if (!playable) return@pointerInput
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (drag.value < -threshold) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            vm.swipeCard(card)
                                        }
                                        scope.launch { drag.animateTo(0f, spring()) }
                                    },
                                    onDragCancel = { scope.launch { drag.animateTo(0f, spring()) } },
                                ) { change, dy ->
                                    change.consume()
                                    scope.launch { drag.snapTo((drag.value + dy / density).coerceIn(-cardH * 2.5f, 0f)) }
                                }
                            }
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
