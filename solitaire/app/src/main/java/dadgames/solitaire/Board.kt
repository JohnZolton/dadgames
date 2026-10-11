package dadgames.solitaire

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dadgames.cards.Card
import dadgames.ui.EmptySlot
import dadgames.ui.P

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import solitaire.core.Klondike
import solitaire.core.Loc
import solitaire.core.Move
import solitaire.core.PileType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

// ---------------------------------------------------------------- geometry (all in dp)

/** Table geometry from the real board size: 7 columns of cards across the full width. [BEST_PRACTICES #10] */
class Dims(val width: Float, val height: Float, val left: Boolean, val draw: Int) {
    val pad = 3f
    val gap = 3f
    val cw = (width - 2 * pad - 6 * gap) / 7f
    val ch = cw * 1.42f
    val topY = 4f
    val tabTop = topY + ch + 14f
    fun colX(i: Int) = pad + i * (cw + gap)
    val stockX get() = colX(if (left) 0 else 6)
    val wasteX get() = colX(if (left) 1 else 4)
    fun foundationX(i: Int) = colX(if (left) 3 + i else i)
    /** Draw 3: the top three waste cards fan out sideways. */
    val fan = (cw + gap) * 0.42f
    /** Face-down cards peek out by a sliver; face-up cards show their whole big index. */
    val downStep = cw * 0.17f
    val upStep = cw * 0.58f
    val minDownStep = cw * 0.08f
    val minUpStep = cw * 0.3f

    fun foundationRect(i: Int) = Rect(foundationX(i), topY, foundationX(i) + cw, topY + ch)
    val stockRect get() = Rect(stockX, topY, stockX + cw, topY + ch)
}

data class Place(val card: Card, val x: Float, val y: Float, val z: Int, val faceUp: Boolean, val loc: Loc, val index: Int)

class Layout(val d: Dims, val places: List<Place>, val colBottom: FloatArray) {
    val byCard: Map<Card, Place> = places.associateBy { it.card }
    fun rectOf(p: Place) = Rect(p.x, p.y, p.x + d.cw, p.y + d.ch)
    fun columnRect(i: Int) = Rect(d.colX(i), d.tabTop, d.colX(i) + d.cw, max(colBottom[i], d.tabTop + d.ch) + d.ch * 0.4f)
}

fun layout(g: Klondike, d: Dims): Layout {
    val out = ArrayList<Place>(52)
    g.stock.forEachIndexed { i, c -> out += Place(c, d.stockX, d.topY, i, false, Loc.STOCK, i) }
    val w = g.waste
    val fanned = if (g.draw == 3) min(3, w.size) else 1
    w.forEachIndexed { i, c ->
        val k = i - (w.size - fanned) // position within the visible fan
        val x = d.wasteX + if (k > 0) k * d.fan else 0f
        out += Place(c, x, d.topY, i, true, Loc.WASTE, i)
    }
    g.foundations.forEachIndexed { f, pile ->
        pile.forEachIndexed { i, c -> out += Place(c, d.foundationX(f), d.topY, i, true, Loc.foundation(f), i) }
    }
    val bottoms = FloatArray(7)
    val avail = d.height - d.tabTop - d.ch - 6f
    for (col in 0..6) {
        val cards = g.tableau[col]
        val down = g.faceDown[col]
        val up = (cards.size - down - 1).coerceAtLeast(0)
        var ds = d.downStep
        var us = d.upStep
        // Long columns squeeze: face-down cards first, then the face-up ones.
        if (down * ds + up * us > avail) {
            ds = max(d.minDownStep, if (down > 0) (avail - up * us) / down else ds)
            if (down * ds + up * us > avail && up > 0) us = max(d.minUpStep, (avail - down * ds) / up)
        }
        var y = d.tabTop
        cards.forEachIndexed { i, c ->
            out += Place(c, d.colX(col), y, i, i >= down, Loc.tableau(col), i)
            bottoms[col] = y + d.ch
            y += if (i < down) ds else us
        }
        if (cards.isEmpty()) bottoms[col] = d.tabTop + d.ch
    }
    return Layout(d, out, bottoms)
}

/** Deal order of a tableau card (row by row, like dealing by hand), for the deal animation. */
private fun dealOrder(p: Place): Int =
    if (p.loc.type != PileType.TABLEAU) 0 else p.index * 7 - p.index * (p.index - 1) / 2 + (p.loc.index - p.index)

private fun overlap(a: Rect, b: Rect): Float {
    val w = min(a.right, b.right) - max(a.left, b.left)
    val h = min(a.bottom, b.bottom) - max(a.top, b.top)
    return if (w > 0 && h > 0) w * h else 0f
}

// ---------------------------------------------------------------- board

@Composable
fun Board(vm: GameViewModel, g: Klondike, modifier: Modifier = Modifier) {
    val settings = vm.settings
    val back = cardBacks.getOrElse(settings.cardBack) { cardBacks[0] }
    val density = LocalDensity.current.density
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val d = remember(maxWidth, maxHeight, settings.leftHanded, g.draw) { Dims(maxWidth.value, maxHeight.value, settings.leftHanded, g.draw) }
        val lay = layout(g, d)
        val layState = rememberUpdatedState(lay)
        val cw = d.cw.dp
        val anims = remember { HashMap<Card, Animatable<Offset, AnimationVector2D>>() }
        val animate = settings.animations

        // Drag state: plain floats updated straight from the gesture. [BEST_PRACTICES #15]
        var dragCards by remember { mutableStateOf<List<Card>>(emptyList()) }
        val dragX = remember { mutableFloatStateOf(0f) }
        val dragY = remember { mutableFloatStateOf(0f) }

        // Little shake when a tapped card has nowhere to go.
        val shakeAnim = remember { Animatable(0f) }
        LaunchedEffect(vm.shake) {
            if (vm.shake == null) return@LaunchedEffect
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            shakeAnim.snapTo(0f)
            shakeAnim.animateTo(0f, keyframes { durationMillis = 320; 7f at 50; -6f at 120; 4f at 190; -2f at 260 })
        }
        val shaking = vm.shake?.cards?.toSet() ?: emptySet()

        // Win cascade: cards leap off the foundations one by one.
        val dealing = remember(vm.dealCount) { vm.takeDeal() }
        var cascadeStart by remember(vm.dealCount) { mutableLongStateOf(0L) }
        var cascadeNow by remember(vm.dealCount) { mutableLongStateOf(0L) }
        var cascadeRan by remember(vm.dealCount) { mutableStateOf(false) }
        val cascade = remember(vm.dealCount, g.won) { if (g.won) cascadeOrder(g) else emptyList() }
        val trajectories = remember(vm.dealCount, g.won, d) { if (g.won) cascade.mapIndexed { i, c -> trajectory(lay.byCard[c]!!, d, Random(i * 31 + 7)) } else emptyList() }
        LaunchedEffect(vm.celebrating) {
            if (!vm.celebrating || cascade.isEmpty()) return@LaunchedEffect
            cascadeRan = true
            delay(250) // let the last card land
            val t0 = withFrameNanos { it }
            cascadeStart = t0
            while (true) {
                val t = withFrameNanos { it }
                cascadeNow = t
                val secs = (t - t0) / 1e9f
                if (secs > LAUNCH_GAP * cascade.size + trajectories.lastOrNull().orEmptyDuration()) break
            }
            vm.celebrationDone()
        }
        val cascadeSecs = if (cascadeStart == 0L) -1f else (cascadeNow - cascadeStart) / 1e9f
        val launchedCount = when {
            cascadeSecs >= 0 -> min(cascade.size, (cascadeSecs / LAUNCH_GAP).toInt() + 1)
            cascadeRan && !vm.celebrating -> cascade.size
            else -> 0
        }
        val hidden = if (launchedCount > 0) cascade.subList(0, launchedCount).toSet() else emptySet()

        // ---- empty slots
        val emptyStockGlyph = g.stock.isEmpty() && g.waste.isNotEmpty()
        Box(Modifier.offset(d.stockX.dp, d.topY.dp).zIndex(-1f)) {
            EmptySlot(cw)
            if (emptyStockGlyph) Icon(Icons.recycle, "Turn the cards back over", tint = P.text.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center).size(cw * 0.6f))
        }
        for (f in 0..3) Box(Modifier.offset(d.foundationX(f).dp, d.topY.dp).zIndex(-1f)) { EmptySlot(cw, "A") }
        for (c in 0..6) Box(Modifier.offset(d.colX(c).dp, d.tabTop.dp).zIndex(-1f)) { EmptySlot(cw) }

        // ---- hint highlight
        val hint = vm.hint
        val hintCards: Set<Card> = when (val m = hint?.move) {
            is Move.Transfer -> g.pile(m.from).takeLast(m.count).toSet()
            Move.Draw -> setOfNotNull(g.stock.lastOrNull())
            else -> emptySet()
        }
        val hintRect: Rect? = when (val m = hint?.move) {
            is Move.Transfer -> when (m.to.type) {
                PileType.FOUNDATION -> d.foundationRect(m.to.index)
                PileType.TABLEAU -> g.tableau[m.to.index].lastOrNull()?.let { lay.rectOf(lay.byCard[it]!!) } ?: Rect(d.colX(m.to.index), d.tabTop, d.colX(m.to.index) + d.cw, d.tabTop + d.ch)
                else -> null
            }
            Move.Draw -> if (g.stock.isEmpty()) d.stockRect else null
            else -> null
        }

        // ---- cards
        val stockPos = Offset(d.stockX * density, d.topY * density)
        for (p in lay.places) {
            if (p.card in hidden) continue
            key(p.card) {
                CardSprite(
                    p, cw, density, back, anims, animate,
                    dealKey = vm.dealCount, animateDeal = dealing, stockPos = stockPos,
                    dragged = p.card in dragCards, dragX = dragX, dragY = dragY,
                    shake = if (p.card in shaking) shakeAnim else null,
                    glow = p.card in hintCards,
                )
            }
        }
        if (hintRect != null) {
            val pulse by rememberInfiniteTransition(label = "hint").animateFloat(0.4f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "hint")
            Box(
                Modifier.offset(hintRect.left.dp - 3.dp, hintRect.top.dp - 3.dp).size(hintRect.width.dp + 6.dp, hintRect.height.dp + 6.dp).zIndex(3000f)
                    .border(3.dp, GOLD.copy(alpha = pulse), RoundedCornerShape(d.cw * 0.12f))
            )
        }

        // ---- cascade cards
        if (cascadeSecs >= 0) {
            for (i in 0 until launchedCount) {
                val pos = trajectories[i].at(cascadeSecs - i * LAUNCH_GAP)
                BigCard(cascade[i], cw, Modifier.offset { IntOffset((pos.x * density).roundToInt(), (pos.y * density).roundToInt()) }.zIndex(4000f + i))
            }
        }

        // ---- gestures: one handler for the whole table
        Box(
            Modifier.fillMaxSize().zIndex(5000f).pointerInput(d) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (vm.celebrating) { vm.celebrationDone(); return@awaitEachGesture }
                    val g0 = vm.game ?: return@awaitEachGesture
                    // Lay out from the latest state, not the last frame drawn, so a quick touch right after
                    // a move still picks the card that is now there.
                    val l = layout(g0, layState.value.d)
                    val pt = Offset(down.position.x / density, down.position.y / density)
                    val hit = l.places.filter { l.rectOf(it).contains(pt) }.maxByOrNull { it.z }
                    val onStock = l.d.stockRect.contains(pt)
                    // What can be picked up: a face-up run, the waste top, a foundation top.
                    val pickable = hit != null && hit.faceUp && when (hit.loc.type) {
                        PileType.TABLEAU -> g0.isRun(hit.loc.index, hit.index)
                        PileType.WASTE, PileType.FOUNDATION -> hit.index == g0.pile(hit.loc).lastIndex
                        PileType.STOCK -> false
                    }
                    var dragging = false
                    var total = Offset.Zero
                    val slop = viewConfiguration.touchSlop
                    var cards: List<Card> = emptyList()
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        // Absolute position, so motion that arrives with the "up" event isn't lost.
                        total = ch.position - down.position
                        if (dragging) { dragX.floatValue = total.x; dragY.floatValue = total.y }
                        if (!ch.pressed) {
                            if (dragging) {
                                ch.consume()
                                val from = hit!!.loc
                                val count = cards.size
                                val lead = l.rectOf(hit).translate(dragX.floatValue / density, dragY.floatValue / density)
                                val target = bestDrop(l, vm, from, count, lead)
                                // Hand-off: the cards continue from exactly where the finger left them. [BEST_PRACTICES #14]
                                scope.launch {
                                    val off = Offset(dragX.floatValue, dragY.floatValue)
                                    for (c in cards) anims[c]?.let { it.snapTo(it.value + off) }
                                    dragX.floatValue = 0f; dragY.floatValue = 0f
                                    dragCards = emptyList()
                                    val moved = target != null && vm.drop(from, count, target)
                                    if (!moved) {
                                        // Glide back from where it was released.
                                        val home = layState.value
                                        for (c in cards) {
                                            val p = home.byCard[c] ?: continue
                                            val a = anims[c] ?: continue
                                            launch { a.animateTo(Offset(p.x * density, p.y * density), tween(if (animate) 260 else 1, easing = FastOutSlowInEasing)) }
                                        }
                                    }
                                }
                            } else {
                                when {
                                    hit != null && hit.loc.type != PileType.STOCK -> vm.tap(hit.loc, hit.index)
                                    hit != null || onStock -> vm.tap(Loc.STOCK, 0)
                                }
                            }
                            break
                        }
                        if (!dragging && pickable && total.getDistance() > slop && !vm.autoCompleting) {
                            dragging = true
                            val pile = g0.pile(hit!!.loc)
                            cards = pile.drop(hit.index)
                            dragX.floatValue = total.x; dragY.floatValue = total.y
                            dragCards = cards
                        }
                        if (dragging) ch.consume()
                    }
                    if (dragging && dragCards.isNotEmpty()) {
                        // Gesture cancelled: put the cards back.
                        val off = Offset(dragX.floatValue, dragY.floatValue)
                        val moving = dragCards
                        scope.launch {
                            for (c in moving) anims[c]?.let { it.snapTo(it.value + off) }
                            dragX.floatValue = 0f; dragY.floatValue = 0f
                            dragCards = emptyList()
                            val home = layState.value
                            for (c in moving) { val p = home.byCard[c] ?: continue; launch { anims[c]?.animateTo(Offset(p.x * density, p.y * density), tween(260)) } }
                        }
                    }
                }
            }
        )
    }
}

/** The legal pile the dragged cards overlap most, or null. */
private fun bestDrop(l: Layout, vm: GameViewModel, from: Loc, count: Int, lead: Rect): Loc? {
    var best: Loc? = null
    var bestArea = 0f
    fun consider(to: Loc, r: Rect) {
        if (to == from) return
        val a = overlap(lead, r)
        if (a > bestArea && vm.isLegalDrop(from, count, to)) { best = to; bestArea = a }
    }
    if (count == 1) for (f in 0..3) consider(Loc.foundation(f), l.d.foundationRect(f))
    for (c in 0..6) consider(Loc.tableau(c), l.columnRect(c))
    return best
}

@Composable
private fun CardSprite(
    p: Place,
    cw: Dp,
    density: Float,
    back: Back,
    anims: HashMap<Card, Animatable<Offset, AnimationVector2D>>,
    animate: Boolean,
    dealKey: Int,
    animateDeal: Boolean,
    stockPos: Offset,
    dragged: Boolean,
    dragX: androidx.compose.runtime.MutableFloatState,
    dragY: androidx.compose.runtime.MutableFloatState,
    shake: Animatable<Float, *>?,
    glow: Boolean,
) {
    val target = Offset(p.x * density, p.y * density)
    val anim = remember { Animatable(if (animateDeal && animate) stockPos else target, Offset.VectorConverter).also { anims[p.card] = it } }
    var seenDeal by remember { mutableIntStateOf(-1) }
    LaunchedEffect(target, dealKey) {
        if (seenDeal != dealKey) {
            seenDeal = dealKey
            if (animateDeal && animate) {
                anim.snapTo(stockPos)
                delay(dealOrder(p) * 30L)
                anim.animateTo(target, tween(300, easing = FastOutSlowInEasing))
            } else anim.snapTo(target)
            return@LaunchedEffect
        }
        if (anim.value == target) return@LaunchedEffect
        if (animate) anim.animateTo(target, tween(240, easing = FastOutSlowInEasing)) else anim.snapTo(target)
    }
    val flip by animateFloatAsState(if (p.faceUp) 0f else 180f, if (animate) tween(260) else snap(), label = "flip")
    val z = when {
        dragged -> 2000f + p.z
        anim.isRunning -> 1000f + p.z
        else -> p.z.toFloat()
    }
    val mod = Modifier
        .zIndex(z)
        .offset {
            val dx = if (dragged) dragX.floatValue else 0f
            val dy = if (dragged) dragY.floatValue else 0f
            val sx = (shake?.value ?: 0f) * density
            IntOffset((anim.value.x + dx + sx).roundToInt(), (anim.value.y + dy).roundToInt())
        }
        .graphicsLayer {
            rotationY = if (flip <= 90f) flip else flip - 180f
            cameraDistance = 14f * density
            if (dragged) { scaleX = 1.04f; scaleY = 1.04f; shadowElevation = 12f * density }
        }
    if (flip <= 90f) BigCard(p.card, cw, mod, glow = glow) else CardBackArt(cw, back, mod, glow = glow)
}

// ---------------------------------------------------------------- win cascade

private const val LAUNCH_GAP = 0.09f

/** Kings first, round the four foundations, then queens... like the classic Windows finish. */
private fun cascadeOrder(g: Klondike): List<Card> {
    val out = ArrayList<Card>()
    for (r in 12 downTo 0) for (f in 0..3) g.foundations[f].getOrNull(r)?.let { out += it }
    return out
}

/** Pre-computed bouncing flight (dp, 60 steps a second) until the card leaves the screen. */
private class Trajectory(val xs: FloatArray, val ys: FloatArray) {
    fun at(t: Float): Offset {
        if (t <= 0f) return Offset(xs[0], ys[0])
        val i = min(xs.size - 1, (t * 60f).toInt())
        return Offset(xs[i], ys[i])
    }
    val duration: Float get() = xs.size / 60f
}

private fun Trajectory?.orEmptyDuration(): Float = this?.duration ?: 0f

private fun trajectory(start: Place, d: Dims, rnd: Random): Trajectory {
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    var x = start.x
    var y = start.y
    val dir = if (rnd.nextBoolean()) 1f else -1f
    val vx = dir * (90f + rnd.nextFloat() * 170f)
    var vy = -(rnd.nextFloat() * 260f)
    val gravity = 1400f
    val floor = d.height - d.ch
    val dt = 1f / 60f
    var steps = 0
    while (steps < 60 * 7 && x > -d.cw - 4 && x < d.width + 4) {
        xs += x; ys += y
        vy += gravity * dt
        x += vx * dt
        y += vy * dt
        if (y > floor) { y = floor; vy = -vy * 0.72f }
        steps++
    }
    xs += x; ys += y
    return Trajectory(xs.toFloatArray(), ys.toFloatArray())
}
