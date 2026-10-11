package dadgames.blackjack

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dadgames.blackjack.core.Action
import dadgames.blackjack.core.CHIPS
import dadgames.blackjack.core.DCard
import dadgames.blackjack.core.DoubleRule
import dadgames.blackjack.core.Game
import dadgames.blackjack.core.Outcome
import dadgames.blackjack.core.Phase
import dadgames.blackjack.core.Rules
import dadgames.blackjack.core.TableInfo
import dadgames.blackjack.core.TABLES
import dadgames.blackjack.core.chipsFor
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlinx.coroutines.withTimeoutOrNull
import dadgames.ui.CardBack
import dadgames.ui.P
import dadgames.ui.ResultPanel
import dadgames.ui.Serif
import dadgames.ui.PrimaryButton
import dadgames.ui.centeredGlyph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

// ---------------------------------------------------------------- geometry

/** Delay between cards when the table is swept into the discard tray. */
const val SWEEP_STEP = 14L

/**
 * Everything on the table is placed absolutely in one coordinate space (dp from the top-left of
 * the area inside the system bars), so a card or chip can move between any two places on screen
 * without jumping. Laid out bottom-up so it fits the emulator's 360 x ~700 dp (1080 px at 480 dpi)
 * as well as dad's 411 dp wide screen. [BEST_PRACTICES #10, #14]
 */
class Geo(val w: Float, val h: Float) {
    val dealerW = min(100f, w * 0.23f)
    val dealerY = 102f
    val dealerH = dealerW * 1.42f
    val dealerBubbleY = dealerY + dealerH + 6f
    val feltY = dealerBubbleY + 32f
    val playerW = min(118f, w * 0.29f)
    val playerH = playerW * 1.42f
    val actionsTop = h - 122f
    /** Room between the cards and the buttons for the auto-deal sign and split-hand chips. */
    val pillTop = actionsTop - 50f
    /** Felt print in play: table name, dealer rule, payout (~80 dp); the player's total bubble sits under it. */
    val playerY = maxOf(feltY + 80f + 40f, pillTop - 8f - playerH - 12f).coerceAtMost(pillTop - 8f - playerH)
    val playerBubbleY = playerY - 36f
    val belowCards = playerY + playerH + 14f

    val shoe = Offset(10f, dealerY + 4f)
    val shoeW = 46f
    val discard = Offset(w - 56f, dealerY + 4f)

    /** Balance pill centre, where won chips go. */
    val bank = Offset(w / 2, 28f)
    /** Where the dealer takes lost chips from / pays winnings out of. */
    val house = Offset(w / 2, dealerY + dealerH * 0.4f)
    /** Bet stack centre while betting. */
    /** Between hands the felt print moves up into the empty dealer area, like his app. */
    val feltBettingY = dealerY + 40f
    val betCentre = Offset(w / 2, feltBettingY + 205f)
    val chipRack = 62f
    val rackRow1 = h - 158f
    val rackRow2 = h - 86f
    val betButtonsY = rackRow1 - 158f

    /** Chip [i] of [n] on the rack: five on the top row, the rest centred underneath. */
    fun rackChip(i: Int, n: Int): Offset {
        val step = (w - 24f) / 5f
        val row = if (i < 5) 0 else 1
        val inRow = if (row == 0) minOf(n, 5) else n - 5
        val k = if (row == 0) i else i - 5
        val x0 = (w - step * inRow) / 2f
        return Offset(x0 + step * (k + 0.5f), (if (row == 0) rackRow1 else rackRow2) + chipRack / 2)
    }
}

/** Where one card sits. */
data class Spot(val x: Float, val y: Float, val w: Float, val faceUp: Boolean, val z: Float, val dim: Boolean = false)

private data class HandBox(val x: Float, val cardW: Float, val width: Float, val y: Float)

/**
 * Card width for [cards] fanned at [INDEX_STEP] within [avail] dp: full size when it fits, otherwise
 * smaller cards rather than a tighter fan, so every card's rank and suit stay visible. [review #3]
 */
private fun fanWidth(cards: Int, avail: Float, maxW: Float): Float =
    min(maxW, avail / (1f + INDEX_STEP * (cards - 1).coerceAtLeast(0)))

private fun handBoxes(g: Game, geo: Geo): List<HandBox> {
    val n = g.hands.size
    if (n <= 1) {
        val cards = g.hands.firstOrNull()?.cards?.size ?: 2
        // Left of the hand: the bet stack (to x = 82). Right: the screen edge.
        val minX = 86f
        val avail = geo.w - minX - 8f
        val cw = fanWidth(cards, avail, geo.playerW)
        val total = cw * (1f + INDEX_STEP * (cards - 1))
        val x = maxOf(minX, (geo.w - total) / 2f).coerceAtMost(geo.w - 8f - total)
        // Smaller cards keep their bottom edge where the full-size ones end.
        return listOf(HandBox(x, cw, total, geo.playerY + (geo.playerH - cw * 1.42f)))
    }
    val slot = (geo.w - 8f) / n
    val base = min(geo.playerW, floatArrayOf(0f, 0f, 86f, 72f, 60f)[n])
    return g.hands.mapIndexed { i, h ->
        val cards = h.cards.size
        val cw = fanWidth(cards, slot - 8f, base)
        val total = cw * (1f + INDEX_STEP * (cards - 1))
        HandBox(4f + slot * i + (slot - total) / 2f, cw, total, geo.playerY + 6f)
    }
}

private fun cardStep(box: HandBox, cards: Int): Float = if (cards <= 1) 0f else (box.width - box.cardW) / (cards - 1)

fun layoutCards(g: Game, geo: Geo, sweeping: Boolean): Map<Int, Spot> {
    val out = HashMap<Int, Spot>()
    var z = 0f
    // Dealer
    val dn = g.dealer.size
    // Between the shoe gauge and the discard tray.
    val dw = fanWidth(dn, geo.w - 2 * 66f, geo.dealerW)
    val dStep = dw * INDEX_STEP
    val dx0 = (geo.w - (dw + dStep * (dn - 1))) / 2f
    for ((i, c) in g.dealer.withIndex()) {
        out[c.id] = Spot(dx0 + dStep * i, geo.dealerY, dw, faceUp = !(i == 1 && g.holeHidden), z = z++)
    }
    // Player
    val boxes = handBoxes(g, geo)
    val playing = g.phase == Phase.PLAYER && g.hands.size > 1
    for ((hi, h) in g.hands.withIndex()) {
        val b = boxes[hi]
        val step = cardStep(b, h.cards.size)
        val active = playing && hi == g.active
        // The active split hand is marked by its gold total and the others dimming (no lift: no room on a small screen).
        for ((i, c) in h.cards.withIndex()) {
            // Doubled card lies across like in a casino? Keep it straight but a bit lower so it reads as the double.
            val dy = i * 4f + if (h.doubled && i == 2) 8f else 0f
            out[c.id] = Spot(b.x + step * i, b.y + dy, b.cardW, faceUp = true, z = z++, dim = playing && !active)
        }
    }
    if (sweeping) {
        for ((id, s) in out) out[id] = Spot(geo.discard.x, geo.discard.y + 2f, geo.shoeW, faceUp = false, z = s.z + 100f)
    }
    return out
}

// ---------------------------------------------------------------- screen

@Composable
fun TableScreen(vm: GameViewModel) {
    // The table is a fixed layout: let large system fonts grow its text a little, not enough to collide.
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(TABLE_FONT_SCALE_MAX))) {
        Table(vm)
    }
}

/** Largest font scale honoured on the table (menus follow the system setting fully). */
const val TABLE_FONT_SCALE_MAX = 1.1f

@Composable
private fun Table(vm: GameViewModel) {
    val g = vm.game
    val t = vm.table
    val felt = Brush.radialGradient(
        0f to Color(t.felt).copy(alpha = 1f).lighten(0.12f), 0.6f to Color(t.felt), 1f to Color(t.feltDark),
        center = Offset(540f, 1000f), radius = 1600f,
    )
    Box(Modifier.fillMaxSize().background(felt)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().systemBarsPadding()
                .pointerInput(Unit) { detectTapGestures { vm.tapFelt() } },
        ) {
            val geo = Geo(maxWidth.value, maxHeight.value)
            FeltPrint(vm, geo, t)
            Shoe(vm, geo)
            Cards(vm, g, geo)
            Bubbles(vm, g, geo)
            BetChips(vm, g, geo)
            Results(vm, g, geo)
            TopBar(vm, geo)
            StatusLine(vm, g, geo)
            if (g.phase == Phase.BETTING && !vm.sweeping) BettingControls(vm, geo)
            if (vm.canAct || (vm.busy && !vm.dealing && (g.phase == Phase.PLAYER || g.phase == Phase.INSURANCE))) ActionBar(vm, g, geo)
            SurrenderButton(vm, g, geo)
        }
        AnimatedVisibility(vm.shortForTable, enter = fadeIn(tween(250)), exit = fadeOut(tween(150))) {
            val to = vm.affordableTable
            if (vm.broke || to == null) ResultPanel("Out of chips") {
                Text(
                    "Here are ${REFILL.commas()} free chips to keep playing. No catch, no shop.",
                    color = P.textDim, fontSize = 17.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Take ${REFILL.short()} chips", Modifier.fillMaxWidth()) { vm.refill() }
            } else ResultPanel("Not enough for ${t.name}") {
                Text(
                    "The smallest bet here is ${t.minBet.short()}. You have ${vm.game.balance.short()}, enough for ${TABLES[to].name}.",
                    color = P.textDim, fontSize = 17.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Play at ${TABLES[to].name}", Modifier.fillMaxWidth()) { vm.selectTable(to) }
            }
        }
    }
}

private fun Color.lighten(f: Float): Color = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)

private fun Modifier.at(x: Float, y: Float): Modifier = this.offset(x.dp, y.dp)

// ---------------------------------------------------------------- felt print and shoe

@Composable
private fun FeltPrint(vm: GameViewModel, geo: Geo, t: TableInfo) {
    val r = vm.feltRules
    val ink = Color.White.copy(alpha = 0.30f)
    val betting = vm.game.phase == Phase.BETTING && !vm.sweeping
    val y by animateFloatAsState(if (betting) geo.feltBettingY else geo.feltY, tween(450, easing = FastOutSlowInEasing), label = "felt")
    // Between hands the print sits level with the shoe and tray, so keep it between them.
    Column(
        Modifier.fillMaxWidth().at(0f, y).padding(horizontal = if (betting) 64.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(t.name.uppercase(), color = Color.Black.copy(alpha = 0.28f), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = if (betting) 30.sp else 26.sp, letterSpacing = 2.sp, maxLines = 1, softWrap = false, lineHeight = if (betting) 34.sp else 28.sp)
        Text(r.dealerLine, color = ink, fontFamily = Serif, fontSize = 15.sp, maxLines = 1, softWrap = false, lineHeight = 19.sp)
        Text(
            "BLACKJACK PAYS 3 TO 2", color = ink, fontFamily = Serif, fontWeight = FontWeight.Bold,
            fontSize = if (betting) 15.sp else 18.sp, letterSpacing = if (betting) 0.5.sp else 1.sp, maxLines = 1, softWrap = false, lineHeight = 22.sp,
        )
        // The extra rules line only shows between hands, when there's room.
        if (betting) Text(rulesLine(r), color = Color.White.copy(alpha = 0.22f), fontSize = 12.sp, maxLines = 2, lineHeight = 15.sp, textAlign = TextAlign.Center)
    }
}

fun rulesLine(r: Rules): String = buildList {
    add("${r.decks} decks")
    add(if (r.doubleRule == DoubleRule.ANY_TWO) "Double any two" else "Double 9-11")
    if (r.maxHands > 1) add("Split to ${r.maxHands}")
    if (r.insurance) add("Insurance")
    if (r.lateSurrender) add("Surrender")
}.joinToString(" · ")

@Composable
private fun Shoe(vm: GameViewModel, geo: Geo) {
    val g = vm.game
    val left = g.shoe.size.toFloat() / g.totalCards.coerceAtLeast(1)
    val pulse by rememberInfiniteTransition(label = "shuffle").animateFloat(0f, 1f, infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "s")
    // Shoe: a few backs stacked, with a fill gauge underneath showing what's left before the cut card.
    Box(Modifier.at(geo.shoe.x, geo.shoe.y).graphicsLayer { if (vm.shuffling) { rotationZ = (pulse - 0.5f) * 8f } }) {
        for (i in 0 until 3) CardBack(geo.shoeW.dp, Modifier.offset(x = (i * 2).dp, y = (i * 2).dp))
    }
    Box(Modifier.at(geo.shoe.x, geo.shoe.y + geo.shoeW * 1.42f + 10f).size(geo.shoeW.dp + 4.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x40000000))) {
        val cut = 1f - g.rules.penetration.toFloat()
        Box(Modifier.fillMaxSize(left.coerceIn(0f, 1f)).background(Color.White.copy(alpha = 0.7f)))
        Box(Modifier.offset(x = ((geo.shoeW + 4f) * cut).dp).size(2.dp, 6.dp).background(Color(0xFFE53935)))
    }
    // Discard tray: thickness grows with the discards; slides into the shoe while shuffling.
    val layers = when {
        g.discards.isEmpty() -> 0
        else -> 1 + (g.discards.size * 5 / g.totalCards.coerceAtLeast(1))
    }
    val move by animateFloatAsState(if (vm.shuffling) 1f else 0f, tween(700, easing = FastOutSlowInEasing), label = "tray")
    if (layers > 0) {
        val x = geo.discard.x + (geo.shoe.x - geo.discard.x) * move
        Box(Modifier.at(x, geo.discard.y).alpha(1f - move * 0.8f)) {
            for (i in 0 until layers) CardBack(geo.shoeW.dp, Modifier.offset(x = (-i * 1.5f).dp, y = (i * 2).dp).graphicsLayer { rotationZ = 6f })
        }
    }
}

// ---------------------------------------------------------------- cards

@Composable
private fun Cards(vm: GameViewModel, g: Game, geo: Geo) {
    val spots = layoutCards(g, geo, vm.sweeping)
    val shoe = Spot(geo.shoe.x, geo.shoe.y, geo.shoeW, faceUp = false, z = 0f)
    val all: List<DCard> = g.dealer + g.hands.flatMap { it.cards }
    for (c in all) {
        val s = spots[c.id] ?: continue
        key(c.id) {
            val isNew = c.id >= vm.batchStart
            val delayMs = if (vm.sweeping) (s.z - 100f).toLong() * SWEEP_STEP else (c.id - vm.batchStart).coerceAtLeast(0) * vm.stagger
            TableCard(c, s, isNew, vm.sweeping, delayMs, vm.flight, shoe)
        }
    }
}

@Composable
private fun TableCard(c: DCard, s: Spot, isNew: Boolean, sweeping: Boolean, delayMs: Long, flight: Long, shoe: Spot) {
    val x = remember { Animatable(if (isNew) shoe.x else s.x) }
    val y = remember { Animatable(if (isNew) shoe.y else s.y) }
    val w = remember { Animatable(if (isNew) shoe.w else s.w) }
    val flip = remember { Animatable(if (isNew || !s.faceUp) 180f else 0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(s) {
        if (first && isNew) delay(delayMs)
        if (sweeping) delay(delayMs)
        first = false
        val spec = tween<Float>(flight.toInt(), easing = FastOutSlowInEasing)
        launch { x.animateTo(s.x, spec) }
        launch { y.animateTo(s.y, spec) }
        launch { w.animateTo(s.w, spec) }
        launch { flip.animateTo(if (s.faceUp) 0f else 180f, tween((flight * 0.9f).toInt(), easing = FastOutSlowInEasing)) }
    }
    val dim by animateFloatAsState(if (s.dim) 1f else 0f, tween(200), label = "dim")
    Box(
        Modifier
            .zIndex(s.z)
            .offset(x.value.dp, y.value.dp)
            .graphicsLayer {
                rotationY = flip.value
                cameraDistance = 14f * density
            },
    ) {
        if (flip.value > 90f) {
            CardBack(w.value.dp, Modifier.graphicsLayer { rotationY = 180f })
        } else {
            BjCard(c.card, w.value.dp)
            if (dim > 0f) Box(Modifier.size(w.value.dp, (w.value * 1.42f).dp).clip(RoundedCornerShape((w.value * 0.09f).dp)).background(Color.Black.copy(alpha = 0.32f * dim)))
        }
    }
}

// ---------------------------------------------------------------- totals

/** Shows [value] only after [delayMs], so a total changes when the card lands, not when it leaves the shoe. */
@Composable
private fun lagged(value: String, delayMs: Long, fresh: Boolean): String? {
    // Fresh cards: no total until the first one lands. Resumed table: show it at once.
    var shown by remember { mutableStateOf(if (fresh && delayMs > 0) null else value) }
    LaunchedEffect(value) { if (value != shown) { delay(delayMs); shown = value } }
    return shown
}

@Composable
private fun Bubbles(vm: GameViewModel, g: Game, geo: Geo) {
    // A total changes when its card lands, not when the card leaves the shoe.
    fun landing(cards: List<DCard>, base: Long = 0): Long {
        val newest = cards.filter { it.id >= vm.batchStart }.maxOfOrNull { it.id - vm.batchStart } ?: return base
        return newest * vm.stagger + vm.flight
    }
    // Dealer
    if (g.dealer.isNotEmpty() && !vm.sweeping) {
        val t = g.visibleDealerTotals
        val label = when {
            !g.holeHidden && g.dealerNatural -> "Blackjack"
            t.bust -> "Bust · ${t.best}"
            !g.holeHidden && g.phase == Phase.ROUND_OVER -> "${t.best}"
            else -> t.label
        }
        val shown = lagged(label, landing(g.dealer, base = vm.flight), fresh = g.dealer.any { it.id >= vm.batchStart })
        if (shown != null) Box(Modifier.zIndex(250f).fillMaxWidth().at(0f, geo.dealerBubbleY), contentAlignment = Alignment.TopCenter) {
            Bubble(shown, highlight = false)
        }
    }
    if (vm.sweeping) return
    val boxes = handBoxes(g, geo)
    for ((i, h) in g.hands.withIndex()) key(h.cards.firstOrNull()?.id ?: -i) {
        val b = boxes[i]
        val active = g.phase == Phase.PLAYER && i == g.active
        val t = h.totals
        val label = when {
            h.natural -> "Blackjack"
            t.bust -> "Bust · ${t.best}"
            else -> t.label
        }
        val shown = lagged(label, landing(h.cards), fresh = h.cards.any { it.id >= vm.batchStart })
        // The hand he's playing: gold glowing total with a bobbing gold arrow. [BEST_PRACTICES #9]
        val yourMove = active && vm.canAct
        if (shown != null) Box(Modifier.zIndex(250f).at(b.x - 30f, b.y - 36f).width((b.width + 60f).dp), contentAlignment = Alignment.TopCenter) {
            // Arrow on the left of the total, or on the right (pointing back) when the hand is at the left edge.
            val bubbleHalf = 14f + shown.length * 6.5f
            val arrowLeft = b.x + b.width / 2f - bubbleHalf - 24f >= 2f
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (yourMove) { if (arrowLeft) TurnArrow() else Spacer(Modifier.width(22.dp)) }
                Bubble(shown, highlight = yourMove)
                if (yourMove) { if (arrowLeft) Spacer(Modifier.width(22.dp)) else TurnArrow(pointLeft = true) }
            }
        }
    }
}

/** Small gold arrow pointing right at the active hand's total, bobbing toward it. */
@Composable
private fun TurnArrow(pointLeft: Boolean = false) {
    val bob by rememberInfiniteTransition(label = "arrow").animateFloat(
        0f, 1f, infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "arrow",
    )
    val gold = P.accent
    Canvas(Modifier.size(22.dp).graphicsLayer { translationX = (bob * 4f - 4f) * density * (if (pointLeft) -1 else 1); if (pointLeft) rotationZ = 180f }) {
        val w = size.width
        val h = size.height
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.95f, h / 2); lineTo(w * 0.15f, h * 0.1f); lineTo(w * 0.35f, h / 2); lineTo(w * 0.15f, h * 0.9f); close()
        }
        drawPath(p, Color(0x66000000), style = Stroke(w * 0.14f))
        drawPath(p, gold)
    }
}

@Composable
private fun Bubble(text: String, highlight: Boolean) {
    val pulse by rememberInfiniteTransition(label = "b").animateFloat(0f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "b")
    val shape = RoundedCornerShape(50)
    Text(
        text,
        Modifier
            .graphicsLayer { if (highlight) { scaleX = 1.04f + 0.05f * pulse; scaleY = scaleX } }
            .shadow(if (highlight) 10.dp else 3.dp, shape, ambientColor = if (highlight) P.accent else Color.Black, spotColor = if (highlight) P.accent else Color.Black)
            .clip(shape)
            .background(if (highlight) P.accent else Color.White)
            .padding(horizontal = 14.dp, vertical = 3.dp),
        color = if (highlight) P.onAccent else Color(0xFF1C1C1C),
        fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
    )
}

// ---------------------------------------------------------------- chips on the felt

private class ChipTarget(val pos: Offset, val alpha: Float)

@Composable
private fun BetChips(vm: GameViewModel, g: Game, geo: Geo) {
    if (g.phase == Phase.BETTING) {
        if (vm.bet > 0 && !vm.sweeping) MovingStack(0, vm.bet, ChipTarget(geo.betCentre, 1f), 64f, null, vm, labelBelow = true)
        return
    }
    val boxes = handBoxes(g, geo)
    val single = g.hands.size == 1
    val stage = if (g.phase == Phase.ROUND_OVER) vm.chipStage else 0
    // Split hands' chips sit on one line under the lowest hand.
    val splitBottom = g.hands.withIndex().maxOfOrNull { (i, h) ->
        boxes[i].y + boxes[i].cardW * 1.42f + 4f * (h.cards.size - 1) + if (h.doubled) 8f else 0f
    } ?: 0f
    for ((i, h) in g.hands.withIndex()) {
        val b = boxes[i]
        // Stacks follow their hand (keyed by its first card), so a split doesn't shuffle them.
        val k = (h.cards.firstOrNull()?.id ?: i) * 4
        // One hand: a big stack left of the cards, like his app. Split hands: a small stack under each hand.
        val size = if (single) 64f else 30f
        val cardsBottom = splitBottom
        // Split stacks: chip + amount centred under their hand. (x is the chip-and-label centre.)
        val home = if (single) Offset(44f, geo.playerY + 80f) else Offset(b.x + b.width / 2f, cardsBottom + 10f + size / 2)
        val origin = if (i == 0) geo.betCentre else Offset(boxes[0].x + boxes[0].width / 2f, home.y)
        val kept = minOf(h.payout, h.bet)
        val won = (h.payout - h.bet).coerceAtLeast(0)
        val lostPart = h.bet - kept
        val settled = g.phase == Phase.ROUND_OVER
        // Bet (or what's left of it after a loss/surrender)
        if (!settled || stage == 0) {
            // Slides out from the bet spot (or the first hand, after a split) only when the cards are new.
            val fresh = h.cards.any { it.id >= vm.batchStart } || (h.split && h.cards.size == 1)
            MovingStack(k, h.bet, ChipTarget(home, 1f), size, if (fresh) origin else null, vm, labelBelow = true)
        } else {
            if (kept > 0) MovingStack(k + 1, kept, ChipTarget(if (stage >= 2) geo.bank else home, if (stage >= 2) 0f else 1f), size, home, vm, labelBelow = stage < 2)
            if (lostPart > 0) key("lost$i") { MovingStack(k + 2, lostPart, ChipTarget(geo.house, 0f), size, home, vm, labelBelow = false) }
            if (won > 0) key("won$i") {
                // Winnings come out of the dealer's tray and land just under the bet.
                val wonHome = if (single) Offset(home.x, home.y + 96f) else Offset(home.x, home.y + 40f)
                MovingStack(k + 3, won, ChipTarget(if (stage >= 2) geo.bank else wonHome, if (stage >= 2) 0f else 1f), size, geo.house, vm, labelBelow = stage < 2)
            }
        }
    }
    if (g.insuranceBet > 0 && g.phase != Phase.ROUND_OVER) {
        // Beside the dealer's cards, under the discard tray: clear of the felt print.
        Box(Modifier.at(geo.w - 78f, geo.dealerY + 96f).width(72.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Chip(chipBreakdown(g.insuranceBet).first(), 34.dp)
                Text("Insured", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

/** A chip stack that glides to [target] (from [from] when it first appears). */
@Composable
private fun MovingStack(slot: Int, amount: Long, target: ChipTarget, size: Float, from: Offset?, vm: GameViewModel, labelBelow: Boolean) {
    key(slot) {
        val start = from ?: target.pos
        val x = remember { Animatable(start.x) }
        val y = remember { Animatable(start.y) }
        val a = remember { Animatable(1f) }
        LaunchedEffect(target.pos, target.alpha) {
            val spec = tween<Float>(520, easing = FastOutSlowInEasing)
            launch { x.animateTo(target.pos.x, spec) }
            launch { y.animateTo(target.pos.y, spec) }
            launch { a.animateTo(target.alpha, tween(520, delayMillis = if (target.alpha < 1f) 250 else 0)) }
        }
        // (x, y) is the centre of the bottom chip.
        val top = y.value - (size + size * 0.09f * 5 - size / 2f)
        val label = @Composable {
            Text(
                amount.short(), color = Color.White, fontSize = if (size > 50f) 20.sp else 16.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false,
            )
        }
        if (size > 50f) Column(
            Modifier.zIndex(300f).at(x.value - size / 2f - 20f, top).width((size + 40f).dp).alpha(a.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ChipStack(amount, size.dp)
            if (labelBelow) label()
        } else Box(
            // Centred on x, so the 4th split hand's amount stays clear of the screen edge.
            Modifier.zIndex(300f).at(x.value - 60f, top).width(120.dp).alpha(a.value),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                ChipStack(amount, size.dp)
                if (labelBelow) Box(Modifier.padding(start = 4.dp, bottom = 4.dp)) { label() }
            }
        }
    }
}

// ---------------------------------------------------------------- results

private fun outcomeColor(o: Outcome): Color = when (o) {
    Outcome.BLACKJACK -> Color(0xFFEBC66E)
    Outcome.WIN -> Color(0xFF3CCB6B)
    Outcome.PUSH -> Color(0xFFE6E6E6)
    Outcome.SURRENDER -> Color(0xFFB0B4BA)
    Outcome.LOSE, Outcome.BUST -> Color(0xFFE5534B)
}

@Composable
private fun Results(vm: GameViewModel, g: Game, geo: Geo) {
    val show = g.phase == Phase.ROUND_OVER && vm.chipStage >= 1 && !vm.sweeping
    if (!show) return
    val boxes = handBoxes(g, geo)
    for ((i, h) in g.hands.withIndex()) {
        val o = h.outcome ?: continue
        val b = boxes[i]
        val small = g.hands.size > 2
        // Insured against a dealer blackjack: the hand loses but insurance pays it back.
        val insured = o == Outcome.LOSE && g.summary?.insuranceWon == true
        val text = when (o) {
            Outcome.PUSH -> if (g.summary?.insuranceWon == true) "Even money" else "Push"
            Outcome.BUST -> "Bust"
            Outcome.LOSE -> if (insured) (if (g.summary?.net == 0L) "Even" else "Insured") else "Lose"
            Outcome.SURRENDER -> "Half back"
            Outcome.WIN -> if (small) h.net.signed() else "Win ${h.net.signed()}"
            Outcome.BLACKJACK -> "Blackjack! ${h.net.signed()}"
        }
        val cx = b.x + b.width / 2f
        val wBox = if (g.hands.size == 1) geo.w else (geo.w - 8f) / g.hands.size
        Box(Modifier.zIndex(400f).at(cx - wBox / 2f, b.y + geo.playerH * 0.32f).width(wBox.dp), contentAlignment = Alignment.Center) {
            val appear = remember { Animatable(0.6f) }
            LaunchedEffect(Unit) { appear.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
            Text(
                text,
                Modifier.graphicsLayer { scaleX = appear.value; scaleY = appear.value }
                    .shadow(8.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
                    .background(if (insured) outcomeColor(Outcome.PUSH) else outcomeColor(o)).padding(horizontal = if (small) 10.dp else 18.dp, vertical = 6.dp),
                color = Color(0xFF1C1C1C), fontSize = if (small) 17.sp else 24.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, softWrap = false,
            )
        }
    }
}

// ---------------------------------------------------------------- top bar and status

@Composable
private fun TopBar(vm: GameViewModel, geo: Geo) {
    Row(
        Modifier.zIndex(700f).fillMaxWidth().height(58.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelBadge(vm.profile.level, vm.profile.xp)
        Spacer(Modifier.weight(1f))
        BalancePill(vm.shownBalance)
        Spacer(Modifier.weight(1f))
        SmallRound(Icon.TABLES, enabled = !vm.midHand && !vm.busy) { vm.screen = Screen.TABLES }
        Spacer(Modifier.width(8.dp))
        SmallRound(Icon.MENU) { vm.leaveTable() }
    }
}

@Composable
fun LevelBadge(level: Int, xp: Int, size: Float = 54f) {
    val frac = xp.toFloat() / xpToNext(level)
    val gold = P.accent
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xE6101A14))
            val st = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
            val inset = 3.dp.toPx()
            drawArc(Color.White.copy(alpha = 0.15f), 0f, 360f, false, Offset(inset, inset), androidx.compose.ui.geometry.Size(this.size.width - inset * 2, this.size.height - inset * 2), style = st)
            drawArc(gold, -90f, 360f * frac.coerceIn(0f, 1f), false, Offset(inset, inset), androidx.compose.ui.geometry.Size(this.size.width - inset * 2, this.size.height - inset * 2), style = st)
        }
        // Sized in dp from the badge (not sp) so a large system font can't overflow the ring. [12a]
        val d = LocalDensity.current
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Level", color = Color.White.copy(alpha = 0.8f), fontSize = with(d) { (size * 0.2f).dp.toSp() }, style = centeredGlyph)
            Text("$level", color = Color.White, fontSize = with(d) { (size * if (level >= 1000) 0.27f else 0.33f).dp.toSp() }, fontWeight = FontWeight.Bold, style = centeredGlyph, maxLines = 1, softWrap = false)
        }
    }
}

/** Balance that counts up/down smoothly (Long-exact; floats can't hold 559,864,578). */
@Composable
fun BalancePill(target: Long) {
    var shown by remember { mutableLongStateOf(target) }
    LaunchedEffect(target) {
        val from = shown
        if (from == target) return@LaunchedEffect
        animate(0f, 1f, animationSpec = tween(650, easing = FastOutSlowInEasing)) { v, _ -> shown = from + ((target - from) * v.toDouble()).toLong() }
        shown = target
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0xE6101A14)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(start = 6.dp, end = 16.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(1_000_000, 30.dp, label = "")
        Spacer(Modifier.width(8.dp))
        Text(shown.commas(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun SmallRound(icon: Icon, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = if (enabled) 0.92f else 0.3f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { IconGlyph(icon, Modifier.size(28.dp), Color(0xFF1C2A22)) }
}

@Composable
private fun StatusLine(vm: GameViewModel, g: Game, geo: Geo) {
    val gold = P.accent
    var yours = false
    val text: String? = when {
        vm.shuffling -> "Shuffling the shoe…"
        vm.message != null -> vm.message
        g.phase == Phase.INSURANCE && vm.canAct -> { yours = true; "Insurance? It costs ${(g.hands[0].bet / 2).short()}" }
        vm.showHint && vm.hintAction != null -> { yours = true; "Hint: ${hintWords(vm.hintAction!!)}" }
        g.phase == Phase.PLAYER && vm.canAct -> {
            yours = true
            if (g.hands.size > 1) "Your turn · hand ${g.active + 1} of ${g.hands.size}" else "Your turn"
        }
        g.phase == Phase.DEALER -> "Dealer's turn"
        g.phase == Phase.ROUND_OVER && vm.chipStage >= 1 -> roundWords(g)
        g.phase == Phase.BETTING && vm.bet == 0L -> "Tap the chips to bet"
        g.phase == Phase.BETTING -> "Tap Deal · hold Deal to auto-deal"
        else -> null
    }
    val pulse by rememberInfiniteTransition(label = "s").animateFloat(0f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "s")
    Box(Modifier.zIndex(650f).fillMaxWidth().at(0f, 60f).height(42.dp), contentAlignment = Alignment.Center) {
        when {
            // Auto-deal takes the status line: one obvious place to look, and tap it to stop.
            vm.autoDeal && !vm.shuffling -> AutoDealSign(vm)
            text != null -> Text(
                text,
                Modifier.graphicsLayer { if (yours) { scaleX = 1f + 0.03f * pulse; scaleY = scaleX } },
                color = if (yours) gold else Color.White, fontSize = if (yours) 22.sp else 19.sp,
                fontWeight = if (yours) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, softWrap = false,
            )
        }
    }
}

fun hintWords(a: Action): String = when (a) {
    Action.HIT -> "Hit"
    Action.STAND -> "Stand"
    Action.DOUBLE -> "Double"
    Action.SPLIT -> "Split"
    Action.SURRENDER -> "Give up (surrender)"
    Action.INSURANCE -> "Take insurance"
    Action.NO_INSURANCE -> "No insurance"
}

/** One line for the whole round. Split rounds give the net result (each hand has its own label). */
fun roundWords(g: Game): String {
    val s = g.summary ?: return ""
    return when {
        s.insuranceWon && s.net == 0L -> "Insurance paid. You break even"
        s.insuranceWon && s.net > 0 -> "Even money: you win ${s.net.short()}"
        g.hands.all { it.surrendered } -> "Surrendered. Half your bet back"
        g.hands.size > 1 -> when {
            s.net > 0 -> "Split hands: you win ${s.net.short()}"
            s.net < 0 -> "Split hands: you lose ${(-s.net).short()}"
            else -> "Split hands: you break even"
        }
        s.net > 0 -> "You win ${s.net.short()}"
        s.net < 0 -> when {
            s.dealerBlackjack -> "Dealer has blackjack"
            g.hands[0].totals.bust -> "Bust. Dealer wins"
            else -> "Dealer wins"
        }
        else -> "Push. Bet returned"
    }
}

// ---------------------------------------------------------------- actions

private data class Btn(val action: Action?, val label: String, val icon: Icon, val color: Color)

private val STAND_BTN = Btn(Action.STAND, "Stand", Icon.STAND, Color(0xFFE8483B))
private val SPLIT_BTN = Btn(Action.SPLIT, "Split", Icon.SPLIT, Color(0xFFC04FD8))
private val DOUBLE_BTN = Btn(Action.DOUBLE, "Double", Icon.DOUBLE, Color(0xFF3D8BEB))
private val HIT_BTN = Btn(Action.HIT, "Hit", Icon.HIT, Color(0xFF2FBF5B))
private val NO_BTN = Btn(Action.NO_INSURANCE, "No", Icon.NO, Color(0xFF8A8F98))
private val INS_BTN = Btn(Action.INSURANCE, "Insure", Icon.INSURANCE, Color(0xFFD9A93A))

/**
 * Fixed slots, same place every hand: Stand, Split, Double, Hit, then the hint bulb. Moves that
 * aren't allowed right now are greyed out, never removed, so a repeated tap can't land on a
 * different button. Insurance uses the outer slots (No where Stand is, Insure where Hit is).
 * [BEST_PRACTICES 12y]
 */
@Composable
private fun ActionBar(vm: GameViewModel, g: Game, geo: Geo) {
    val legal = g.legal()
    val slots: List<Btn?> = if (g.phase == Phase.INSURANCE) listOf(NO_BTN, null, null, INS_BTN)
    else listOf(STAND_BTN, SPLIT_BTN, DOUBLE_BTN, HIT_BTN)
    val hinted = if (vm.showHint || vm.settings.autoHint) vm.hintAction else null
    Row(
        Modifier.zIndex(600f).fillMaxWidth().at(0f, geo.actionsTop).height(118.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        for (b in slots) {
            if (b == null) Spacer(Modifier.weight(1f))
            else RoundAction(b, Modifier.weight(1f), enabled = vm.canAct && b.action in legal, hinted = b.action == hinted) { b.action?.let(vm::act) }
        }
        if (vm.settings.hintButton) {
            val on = vm.showHint
            RoundAction(
                Btn(null, "Hint", Icon.BULB, if (on) P.accent else Color(0xFFF4F1E8)), Modifier.weight(1f),
                enabled = vm.canAct, hinted = false, iconColor = Color(0xFF1C2A22),
            ) { vm.toggleHint() }
        }
    }
}

@Composable
private fun RoundAction(b: Btn, modifier: Modifier, enabled: Boolean, hinted: Boolean, iconColor: Color = Color.White, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "h").animateFloat(0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "h")
    val gold = P.accent
    val grey = Color(0xFF55615A)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(68.dp).graphicsLayer { if (hinted) { scaleX = 1.04f + 0.06f * pulse; scaleY = scaleX } },
            contentAlignment = Alignment.Center,
        ) {
            if (hinted) Box(Modifier.size(68.dp).clip(CircleShape).background(gold.copy(alpha = 0.55f + 0.45f * pulse)))
            Box(
                Modifier.size(56.dp).shadow(if (enabled) 6.dp else 0.dp, CircleShape).clip(CircleShape)
                    .background(if (enabled) b.color else grey.copy(alpha = 0.55f))
                    .clickable(enabled = enabled, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                IconGlyph(
                    b.icon, Modifier.size(32.dp), if (enabled) iconColor else Color.White.copy(alpha = 0.35f),
                    bg = if (enabled) b.color else Color.Transparent,
                )
            }
        }
        Text(
            b.label, color = when { hinted -> gold; enabled -> Color.White; else -> Color.White.copy(alpha = 0.4f) },
            fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
        )
    }
}

/**
 * Surrender (only when the rule is on and it's allowed: first two cards, one hand). It sits in
 * the strip above the buttons, left side, so the button row never grows to six.
 */
@Composable
private fun SurrenderButton(vm: GameViewModel, g: Game, geo: Geo) {
    if (!g.rules.lateSurrender || g.phase != Phase.PLAYER || g.hands.size != 1 || g.hands[0].cards.size != 2) return
    val enabled = vm.canAct && Action.SURRENDER in g.legal()
    val hinted = enabled && (vm.showHint || vm.settings.autoHint) && vm.hintAction == Action.SURRENDER
    val pulse by rememberInfiniteTransition(label = "sr").animateFloat(0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "sr")
    Row(
        Modifier.zIndex(600f).at(10f, geo.pillTop + 4f)
            .clip(RoundedCornerShape(50))
            .background(Color(0xE6101A14))
            .border(2.dp, if (hinted) P.accent.copy(alpha = 0.5f + 0.5f * pulse) else Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
            .clickable(enabled = enabled) { vm.act(Action.SURRENDER) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconGlyph(Icon.SURRENDER, Modifier.size(22.dp), if (hinted) P.accent else Color.White)
        Spacer(Modifier.width(8.dp))
        Text("Give up half", color = if (hinted) P.accent else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/** Gold, pulsing sign in the status line while auto-deal is on; tap it to stop. */
@Composable
private fun AutoDealSign(vm: GameViewModel) {
    val pulse by rememberInfiniteTransition(label = "a").animateFloat(0f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0xE6101A14))
            .border(2.dp, P.accent.copy(alpha = 0.5f + 0.5f * pulse), RoundedCornerShape(50))
            .clickable { vm.stopAutoDeal() }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Auto deal", color = P.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        Text(" · Tap to stop", color = Color.White, fontSize = 17.sp, maxLines = 1, softWrap = false)
    }
}

// ---------------------------------------------------------------- betting

private data class Fly(val n: Int, val value: Long, val from: Offset)

/** How long Deal must be held to start auto-deal: well past a slow tap or the system long-press. [12z] */
const val AUTO_DEAL_HOLD_MS = 800

@Composable
private fun BettingControls(vm: GameViewModel, geo: Geo) {
    val flying = remember { mutableStateListOf<Fly>() }
    var counter by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    // Clear and Deal flank the bet like in his app.
    val by = geo.betButtonsY
    Column(Modifier.at(geo.w * 0.2f - 50f, by).width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(66.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Color(0xFFE8483B).copy(alpha = if (vm.bet > 0) 1f else 0.45f))
                .clickable(enabled = vm.bet > 0) { vm.clearBet() },
            contentAlignment = Alignment.Center,
        ) { IconGlyph(Icon.CLEAR, Modifier.size(36.dp)) }
        Text("Clear", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
    Column(Modifier.at(geo.w * 0.8f - 70f, by).width(140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val pulse by rememberInfiniteTransition(label = "d").animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "d")
        val ready = vm.bet > 0
        val hold = remember { Animatable(0f) }
        val gold = P.accent
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            // Ring fills while Deal is held; full ring = auto-deal.
            Canvas(Modifier.size(84.dp)) {
                if (hold.value > 0f) {
                    val st = 5.dp.toPx()
                    drawArc(
                        gold, -90f, 360f * hold.value, false, Offset(st / 2, st / 2),
                        androidx.compose.ui.geometry.Size(size.width - st, size.height - st), style = Stroke(st, cap = StrokeCap.Round),
                    )
                }
            }
            Box(
                Modifier.size(72.dp).graphicsLayer { if (ready && hold.value == 0f) { scaleX = 1f + 0.04f * pulse; scaleY = scaleX } }
                    .shadow(8.dp, CircleShape).clip(CircleShape).background(Color(0xFF2FBF5B).copy(alpha = if (ready) 1f else 0.45f))
                    .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            val fill = scope.launch { hold.animateTo(1f, tween(AUTO_DEAL_HOLD_MS, easing = androidx.compose.animation.core.LinearEasing)) }
                            val up = withTimeoutOrNull(AUTO_DEAL_HOLD_MS.toLong()) { waitForUpOrCancellation() }
                            fill.cancel()
                            if (up != null) {
                                vm.deal() // released early: a plain deal
                            } else {
                                vm.startAutoDeal() // held the full time
                                waitForUpOrCancellation()
                            }
                            scope.launch { hold.snapTo(0f) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { IconGlyph(Icon.DEAL, Modifier.size(40.dp), bg = Color(0xFF2FBF5B)) }
        }
        Text("Deal", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("(Hold to auto)", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, maxLines = 1, softWrap = false)
    }
    // Chip rack: only chips this table takes (its minimum up to its maximum), in his familiar order.
    val t = vm.table
    val chips = chipsFor(t)
    val rows = if (chips.size > 5) 2 else 1
    Box(
        Modifier.at(6f, geo.rackRow1 - 8f).size((geo.w - 12f).dp, ((geo.rackRow2 - geo.rackRow1) * (rows - 1) + geo.chipRack + 16f).dp)
            .clip(RoundedCornerShape(22.dp)).background(Color(0x55000000)),
    )
    for ((i, v) in chips.withIndex()) {
        val c = geo.rackChip(i, chips.size)
        val canAdd = vm.bet < minOf(t.maxBet, vm.game.balance) && v <= vm.game.balance
        Box(
            Modifier.at(c.x - geo.chipRack / 2, c.y - geo.chipRack / 2).size(geo.chipRack.dp).clip(CircleShape)
                .clickable {
                    if (vm.tapChip(v)) flying.add(Fly(counter++, v, c))
                },
        ) { Chip(v, geo.chipRack.dp, dim = !canAdd) }
    }
    for (f in flying) key(f.n) {
        val p = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            p.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
            flying.remove(f)
        }
        val x = f.from.x + (geo.betCentre.x - f.from.x) * p.value
        val y = f.from.y + (geo.betCentre.y + 10f - f.from.y) * p.value
        val s = geo.chipRack + (64f - geo.chipRack) * p.value
        Chip(f.value, s.dp, Modifier.zIndex(600f).at(x - s / 2, y - s / 2))
    }
}
