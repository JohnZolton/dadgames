package dadgames.blocks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import blocks.core.Bits
import blocks.core.CELLS
import blocks.core.GameState
import blocks.core.Mode
import blocks.core.SIZE
import blocks.core.Shape
import blocks.core.Shapes
import blocks.core.boxOf
import blocks.core.cellOf
import blocks.core.colOf
import blocks.core.rowOf
import dadgames.ui.P
import dadgames.ui.PrimaryButton
import dadgames.ui.ResultPanel
import dadgames.ui.SecondaryButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Board geometry in root pixels, written by the board on layout. */
class BoardGeo {
    var x = 0f
    var y = 0f
    var pitch = 1f
}

enum class DragPhase { IDLE, DRAGGING, DROPPING, RETURNING }

/**
 * The piece being dragged. Position is plain state written straight from the gesture callback
 * [BEST_PRACTICES #15]; animations (lift, drop, glide back) write the same fields, so the piece never jumps.
 */
class DragState {
    var phase by mutableStateOf(DragPhase.IDLE)
    var slot by mutableIntStateOf(-1)
    var shape: Shape? = null
    /** Drawn top-left of the piece and cell pitch, root px. */
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var cell by mutableFloatStateOf(0f)
    /** Board anchor the piece would land on, or -1. */
    var anchor by mutableIntStateOf(-1)

    var downX = 0f; var downY = 0f
    var fingerX = 0f; var fingerY = 0f
    var trayX = 0f; var trayY = 0f; var trayCell = 0f
    var lift = 0f
    var job: Job? = null

    fun clear() {
        phase = DragPhase.IDLE; slot = -1; shape = null; anchor = -1; lift = 0f
    }
}

@Composable
fun GameScreen(vm: GameViewModel) {
    val g = vm.game ?: return
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val geo = remember { BoardGeo() }
    val drag = remember { DragState() }
    var selected by remember { mutableIntStateOf(-1) }
    var tapAnchor by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf<String?>(null) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    val gestures = remember { TrayGestures() }
    val current by rememberUpdatedState(g)

    // A new move clears any tap selection.
    LaunchedEffect(g.moves, g.deals) { selected = -1; tapAnchor = -1 }

    fun placeWithFeedback(slot: Int, anchor: Int): Boolean {
        val ok = vm.place(slot, anchor)
        if (ok && vm.settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        return ok
    }

    // Status messages for combos, streaks, gems and records.
    LaunchedEffect(vm.fx?.id) {
        val fx = vm.fx ?: return@LaunchedEffect
        val parts = buildList {
            if (!fx.gems.isEmpty) add(if (fx.gems.count == 1) "Gem collected!" else "${fx.gems.count} gems collected!")
            if (fx.combo >= 2) add("Combo x${fx.combo}")
            if (fx.streak >= 2) add("Streak ${fx.streak}")
        }
        if (parts.isNotEmpty()) { message = parts.joinToString("   "); delay(1800); message = null }
    }
    var shownToday by remember { mutableStateOf(g.mode != Mode.CLASSIC || g.score > vm.startToday) }
    var shownEver by remember { mutableStateOf(g.mode != Mode.CLASSIC || g.score > vm.startEver) }
    LaunchedEffect(g.score) {
        if (g.mode != Mode.CLASSIC) return@LaunchedEffect
        val text = when {
            !shownEver && vm.startEver > 0 && g.score > vm.startEver -> { shownEver = true; shownToday = true; "New best ever!" }
            !shownToday && vm.startToday > 0 && g.score > vm.startToday -> { shownToday = true; "New best today!" }
            else -> null
        }
        if (text != null) { delay(300); message = text; delay(3000); if (message == text) message = null }
    }

    BoxWithConstraints(Modifier.fillMaxSize().woodBackground()) {
        val sidePad = 8.dp
        val boardSize = min(maxWidth - sidePad * 2, maxHeight * 0.5f)
        Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            TopBar(vm, g)
            StatusLine(vm, g, selected, tapAnchor, message)
            Board(vm, g, drag, geo, boardSize, selected, tapAnchor) { cell ->
                // Tap-to-place: first tap shows the shadow, a tap on the shadow places it.
                val hint = vm.hint
                if (selected < 0) {
                    if (hint != null && g.preview(hint.slot, hint.anchor)?.cells?.get(cell) == true) placeWithFeedback(hint.slot, hint.anchor)
                    return@Board
                }
                val shape = Shapes[g.tray[selected]]
                val current = if (tapAnchor >= 0) g.preview(selected, tapAnchor) else null
                if (current != null && current.cells[cell]) {
                    placeWithFeedback(selected, tapAnchor)
                    selected = -1; tapAnchor = -1
                } else {
                    tapAnchor = anchorCovering(g, selected, shape, cell)
                }
            }
            Spacer(Modifier.height(10.dp))
            Tray(vm, g, drag, geo, selected, gestures)
            Spacer(Modifier.weight(1f))
            BottomBar(vm, g)
        }

        // Gesture callbacks from the tray (root px). They read the latest game through `current`.
        gestures.grab = { slot, x, y ->
            drag.job?.cancel()
            selected = -1; tapAnchor = -1
            drag.slot = slot; drag.shape = Shapes[current.tray[slot]]
            drag.downX = x; drag.downY = y; drag.fingerX = x; drag.fingerY = y
            drag.lift = 0f
            drag.phase = DragPhase.DRAGGING
            recompute(drag, geo, current, density.density)
            drag.job = scope.launch {
                animate(0f, 1f, animationSpec = tween(140, easing = FastOutSlowInEasing)) { v, _ ->
                    drag.lift = v; recompute(drag, geo, current, density.density)
                }
            }
        }
        gestures.move = { x, y ->
            drag.fingerX = x; drag.fingerY = y; recompute(drag, geo, current, density.density)
        }
        gestures.release = release@{ tap ->
            if (drag.phase != DragPhase.DRAGGING) return@release
            drag.job?.cancel()
            val slot = drag.slot
            val a = drag.anchor
            if (tap) {
                selected = slot; tapAnchor = -1
                glideBack(drag, scope)
            } else if (a >= 0) {
                drag.phase = DragPhase.DROPPING
                val fx = drag.x; val fy = drag.y; val fc = drag.cell
                val tx = geo.x + colOf(a) * geo.pitch
                val ty = geo.y + rowOf(a) * geo.pitch
                drag.job = scope.launch {
                    animate(0f, 1f, animationSpec = tween(90, easing = LinearEasing)) { v, _ ->
                        drag.x = lerp(fx, tx, v); drag.y = lerp(fy, ty, v); drag.cell = lerp(fc, geo.pitch, v)
                    }
                    if (placeWithFeedback(slot, a)) drag.clear() else glideBack(drag, scope)
                }
            } else glideBack(drag, scope)
        }
        Canvas(Modifier.fillMaxSize().onGloballyPositioned { overlayOrigin = it.positionInRoot() }) {
            val shape = drag.shape
            if (drag.phase != DragPhase.IDLE && shape != null) {
                val c = drag.cell
                val inset = c * 0.04f
                val ox = drag.x - overlayOrigin.x
                val oy = drag.y - overlayOrigin.y
                val lifted = drag.phase == DragPhase.DRAGGING || drag.phase == DragPhase.RETURNING
                for (p in shape.cells) {
                    val bx = ox + p.c * c + inset
                    val by = oy + p.r * c + inset
                    if (lifted) drawRoundRect(Color(0x55000000), Offset(bx + c * 0.08f, by + c * 0.12f), Size(c - inset * 2, c - inset * 2), CornerRadius(c * 0.1f))
                    woodBlock(bx, by, c - inset * 2)
                }
            }
        }

        EndPanel(vm, g)
    }
}

/** Anchor for the selected piece that covers [cell], centred on it where possible. */
private fun anchorCovering(g: GameState, slot: Int, shape: Shape, cell: Int): Int {
    val r = rowOf(cell); val c = colOf(cell)
    val centred = cellOf((r - (shape.rows - 1) / 2).coerceIn(0, SIZE - shape.rows), (c - (shape.cols - 1) / 2).coerceIn(0, SIZE - shape.cols))
    if (g.canPlace(slot, centred) && shape.masks[centred]!![cell]) return centred
    return shape.cells.map { p -> r - p.r to c - p.c }
        .filter { (ar, ac) -> ar in 0 until SIZE && ac in 0 until SIZE }
        .map { (ar, ac) -> cellOf(ar, ac) }
        .firstOrNull { g.canPlace(slot, it) } ?: -1
}

/** Where the lifted piece sits for the current finger position, and where it would land. */
private fun recompute(d: DragState, geo: BoardGeo, g: GameState, density: Float) {
    val shape = d.shape ?: return
    val bc = geo.pitch
    // Fully lifted: board-cell size, centred over the finger, bottom edge well above the fingertip.
    val tx = d.fingerX - shape.cols * bc / 2f
    val ty = d.fingerY - shape.rows * bc - (bc * 0.9f + 26f * density)
    // In the tray, following the finger from where it was grabbed.
    val sx = d.trayX + (d.fingerX - d.downX)
    val sy = d.trayY + (d.fingerY - d.downY)
    d.x = lerp(sx, tx, d.lift)
    d.y = lerp(sy, ty, d.lift)
    d.cell = lerp(d.trayCell, bc, d.lift)
    // Snap using the fully-lifted position so the shadow doesn't wander during the lift.
    val c = ((tx - geo.x) / bc).roundToInt()
    val r = ((ty - geo.y) / bc).roundToInt()
    val a = if (r in 0 until SIZE && c in 0 until SIZE) cellOf(r, c) else -1
    d.anchor = if (a >= 0 && g.canPlace(d.slot, a)) a else -1
}

private fun glideBack(d: DragState, scope: kotlinx.coroutines.CoroutineScope) {
    d.phase = DragPhase.RETURNING
    d.anchor = -1
    val fx = d.x; val fy = d.y; val fc = d.cell
    d.job = scope.launch {
        animate(0f, 1f, animationSpec = tween(260, easing = FastOutSlowInEasing)) { v, _ ->
            d.x = lerp(fx, d.trayX, v); d.y = lerp(fy, d.trayY, v); d.cell = lerp(fc, d.trayCell, v)
        }
        d.clear()
    }
}

/** Callbacks from tray gestures to the screen (reassigned every composition, read during gestures). */
class TrayGestures {
    var grab: (slot: Int, x: Float, y: Float) -> Unit = { _, _, _ -> }
    var move: (Float, Float) -> Unit = { _, _ -> }
    var release: (tap: Boolean) -> Unit = {}
}

// ---------------------------------------------------------------- top bar

@Composable
private fun TopBar(vm: GameViewModel, g: GameState) {
    Row(
        Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WoodIconButton(Icon.BACK, "Back") { vm.leaveGame() }
        Spacer(Modifier.weight(1f))
        val shown by animateIntAsState(g.score, tween(400), label = "score")
        Box(
            Modifier.width(104.dp).height(56.dp).clip(RoundedCornerShape(12.dp)).background(Wood.scoreBox)
                .border(2.dp, Color(0x55FFE2B0), RoundedCornerShape(12.dp))
                .semantics { contentDescription = "Score ${g.score}" },
            contentAlignment = Alignment.Center,
        ) { Text("$shown", color = Color(0xFFFFE9C9), fontSize = 32.fixedSp, fontWeight = FontWeight.Bold, maxLines = 1) }
        Spacer(Modifier.width(10.dp))
        if (g.mode == Mode.CLASSIC) TodayBox(vm, g, shown) else GemBox(g)
        Spacer(Modifier.weight(1f))
        WoodIconButton(Icon.GEAR, "Settings") { vm.openSettings(Screen.GAME) }
    }
}

@Composable
private fun TodayBox(vm: GameViewModel, g: GameState, shownScore: Int) {
    val beat = g.score > vm.startToday
    // Follows the counting-up score so the two boxes agree.
    val value = maxOf(vm.startToday, shownScore)
    Box(contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.width(104.dp).height(56.dp).clip(RoundedCornerShape(12.dp))
                .background(if (beat) Brush.verticalGradient(listOf(Color(0xFFFFD36B), Color(0xFFE9A43A))) else Brush.verticalGradient(listOf(Color(0xFFE47A3C), Wood.todayBox)))
                .semantics { contentDescription = "Today's best $value" },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            val ink = if (beat) Color(0xFF4A2A06) else Color(0xFFFFEBD2)
            Text("TODAY", color = ink, fontSize = 12.fixedSp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 13.fixedSp, maxLines = 1)
            Text("$value", color = ink, fontSize = 24.fixedSp, fontWeight = FontWeight.Bold, lineHeight = 26.fixedSp, maxLines = 1)
        }
        // Crown sits on the top edge, like Woodoku's.
        IconGlyph(Icon.CROWN, 22.dp, if (beat) Wood.glow else Wood.crown, Modifier.offset(y = (-15).dp))
    }
}

@Composable
private fun GemBox(g: GameState) {
    Row(
        Modifier.width(104.dp).height(56.dp).clip(RoundedCornerShape(12.dp)).background(Wood.todayBox)
            .semantics { contentDescription = "${g.gemsCollected} of ${g.gemsTotal} gems" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        IconGlyph(Icon.GEM, 26.dp, Wood.gem)
        Spacer(Modifier.width(6.dp))
        Text("${g.gemsCollected}/${g.gemsTotal}", color = Color(0xFFFFEBD2), fontSize = 24.fixedSp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun StatusLine(vm: GameViewModel, g: GameState, selected: Int, tapAnchor: Int, message: String?) {
    val (text, strong) = when {
        g.over -> "" to false
        message != null -> message to true
        vm.thinking -> "Thinking…" to false
        selected >= 0 && tapAnchor >= 0 -> "Tap the shadow to place it" to true
        selected >= 0 -> "Now tap where it goes" to true
        vm.hint != null -> "Hint: tap the glowing shadow" to true
        g.mode == Mode.DAILY -> "Clear the lines holding gems" to false
        g.moves == 0 -> "Drag a piece onto the board" to false
        // Once a record falls it stays on screen for the rest of the game.
        g.score > vm.startEver -> "New best ever!" to true
        g.score > vm.startToday -> "New best today!" to false
        else -> "" to false
    }
    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text, color = if (strong) Color(0xFFFFE08A) else Color(0xE6FFF1DD), fontSize = if (strong) 20.fixedSp else 18.fixedSp,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------- board

@Composable
private fun Board(
    vm: GameViewModel, g: GameState, drag: DragState, geo: BoardGeo, boardSize: androidx.compose.ui.unit.Dp,
    selected: Int, tapAnchor: Int, onTapCell: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val framePx = with(density) { 6.dp.toPx() }
    val clearT = remember { Animatable(1f) }
    val placeT = remember { Animatable(1f) }
    val fx = vm.fx
    LaunchedEffect(fx?.id) {
        if (fx == null || System.currentTimeMillis() - fx.time > 400) return@LaunchedEffect
        launch { placeT.snapTo(0f); placeT.animateTo(1f, tween(180)) }
        if (!fx.cleared.isEmpty) { clearT.snapTo(0f); clearT.animateTo(1f, tween(560, easing = LinearEasing)) }
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.35f, 0.75f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse",
    )
    val hint = vm.hint

    // For TalkBack (and scripted tests): where the hint shadow is.
    val hintCell = hint?.let { g.preview(it.slot, it.anchor)?.cells?.cells()?.firstOrNull() }
    val boardDescription = "Board" + (hintCell?.let { ", hint at row ${rowOf(it) + 1} column ${colOf(it) + 1}" } ?: "")
    Box(Modifier.size(boardSize).semantics { contentDescription = boardDescription }) {
        Canvas(
            Modifier.fillMaxSize()
                .shadow(6.dp, RoundedCornerShape(10.dp))
                .onGloballyPositioned { c ->
                    val p = c.positionInRoot()
                    geo.x = p.x + framePx
                    geo.y = p.y + framePx
                    geo.pitch = (c.size.width - framePx * 2) / SIZE
                }
                .pointerInput(g) {
                    detectTapGestures { pos ->
                        val pitch = (size.width - framePx * 2) / SIZE
                        val c = ((pos.x - framePx) / pitch).toInt()
                        val r = ((pos.y - framePx) / pitch).toInt()
                        if (r in 0 until SIZE && c in 0 until SIZE) onTapCell(cellOf(r, c))
                    }
                },
        ) {
            val pitch = (size.width - framePx * 2) / SIZE
            drawRoundRect(Brush.verticalGradient(listOf(Wood.frame, Wood.frameDark)), cornerRadius = CornerRadius(framePx * 1.6f))
            val gap = pitch * 0.045f
            fun cx(cell: Int) = framePx + colOf(cell) * pitch
            fun cy(cell: Int) = framePx + rowOf(cell) * pitch
            // Grooves, then the nine boxes shaded alternately.
            drawRect(Wood.groove, Offset(framePx, framePx), Size(pitch * SIZE, pitch * SIZE))
            for (cell in 0 until CELLS) {
                val b = boxOf(cell)
                val dark = ((b / 3) + (b % 3)) % 2 == 0
                drawRect(if (dark) Wood.boxDark else Wood.boxLight, Offset(cx(cell) + gap, cy(cell) + gap), Size(pitch - gap * 2, pitch - gap * 2))
            }

            // Which shadow to show: the dragged piece, the tap-to-place choice, or the hint.
            val dragging = drag.phase == DragPhase.DRAGGING && drag.anchor >= 0
            val preview = when {
                dragging -> g.preview(drag.slot, drag.anchor)
                selected >= 0 && tapAnchor >= 0 -> g.preview(selected, tapAnchor)
                hint != null && drag.phase == DragPhase.IDLE && selected < 0 -> g.preview(hint.slot, hint.anchor)
                else -> null
            }
            val clearing = preview?.clearing ?: Bits.EMPTY
            val inset = pitch * 0.04f
            val bs = pitch - inset * 2
            for (cell in 0 until CELLS) {
                if (!g.board[cell]) continue
                var s = bs
                var off = 0f
                if (fx != null && fx.placed[cell] && placeT.value < 1f) {
                    val k = 1f + 0.08f * (1f - placeT.value)
                    off = (bs * k - bs) / 2f; s = bs * k
                }
                woodBlock(cx(cell) + inset - off, cy(cell) + inset - off, s, glow = clearing[cell])
                if (g.gems[cell]) gem(cx(cell) + inset, cy(cell) + inset, bs)
            }
            if (preview != null) {
                val a = if (dragging || selected >= 0) 0.5f else pulse
                for (cell in preview.cells.cells()) {
                    woodBlock(cx(cell) + inset, cy(cell) + inset, bs, alpha = a, glow = clearing[cell])
                }
            }
            // Clear animation: blocks shrink and fade in a wave spreading from where the piece landed.
            if (fx != null && clearT.value < 1f && !fx.cleared.isEmpty) {
                val pc = fx.placed.cells()
                val pr = pc.map { rowOf(it) }.average().toFloat()
                val pcol = pc.map { colOf(it) }.average().toFloat()
                for (cell in fx.cleared.cells()) {
                    val dist = kotlin.math.hypot(rowOf(cell) - pr, colOf(cell) - pcol) / 11f
                    val p = ((clearT.value * 1.6f) - dist).coerceIn(0f, 1f)
                    if (p >= 1f) continue
                    val k = 1f - p
                    val s = bs * (0.4f + 0.6f * k)
                    val o = (bs - s) / 2f
                    woodBlock(cx(cell) + inset + o, cy(cell) + inset + o, s, alpha = k, glow = true)
                    if (fx.gems[cell]) gem(cx(cell) + inset, cy(cell) + inset - p * pitch * 0.8f, bs, alpha = 1f - p * 0.6f, scale = 1f + p * 0.5f)
                }
            }
        }
        // "+46" floating up from where the piece landed.
        if (fx != null && System.currentTimeMillis() - fx.time < 1500) PointsFloater(fx, framePx)
    }
}

@Composable
private fun PointsFloater(fx: MoveFx, framePx: Float) {
    val t = remember(fx.id) { Animatable(0f) }
    LaunchedEffect(fx.id) { t.animateTo(1f, tween(1000, easing = LinearEasing)) }
    if (t.value >= 1f) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pitchPx = (constraints.maxWidth - framePx * 2) / SIZE
        val pc = fx.placed.cells()
        val cx = framePx + (pc.map { colOf(it) }.average().toFloat() + 0.5f) * pitchPx
        val cy = framePx + (pc.map { rowOf(it) }.average().toFloat() + 0.5f) * pitchPx
        val big = fx.combo > 0
        Text(
            "+${fx.points}",
            color = if (big) Color(0xFFFFE08A) else Color(0xFFFFF4E0),
            fontSize = if (big) 34.fixedSp else 24.fixedSp, fontWeight = FontWeight.Black,
            modifier = Modifier
                .offset {
                    val rise = t.value * 50.dp.toPx()
                    IntOffset((cx - 40.dp.toPx()).toInt(), (cy - 22.dp.toPx() - rise).coerceAtLeast(0f).toInt())
                }
                .width(80.dp)
                .graphicsLayer { alpha = if (t.value < 0.7f) 1f else (1f - t.value) / 0.3f },
            textAlign = TextAlign.Center, maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0xAA3A1A08), Offset(0f, 3f), 6f)),
        )
    }
}

// ---------------------------------------------------------------- tray

@Composable
private fun Tray(
    vm: GameViewModel, g: GameState, drag: DragState, geo: BoardGeo, selected: Int, gestures: TrayGestures,
) {
    val density = LocalDensity.current
    val hint = vm.hint
    val playable = g.playableSlots().toSet()
    // New trays slide in from the right, one after another.
    val firstDeal = remember { g.deals }
    val slide = remember { List(3) { Animatable(0f) } }
    LaunchedEffect(g.deals) {
        if (g.deals == firstDeal) return@LaunchedEffect
        slide.forEachIndexed { i, a ->
            launch { a.snapTo(1f); delay(i * 70L); a.animateTo(0f, tween(320, easing = FastOutSlowInEasing)) }
        }
    }
    val pulse by rememberInfiniteTransition(label = "trayPulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "trayPulse",
    )
    Row(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 8.dp)) {
        for (slot in 0 until 3) {
            var origin by remember { mutableStateOf(Offset.Zero) }
            val id = g.tray[slot]
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .onGloballyPositioned { origin = it.positionInRoot() }
                    .pointerInput(slot, id, g.over) {
                        if (id < 0 || g.over) return@pointerInput
                        val shape = Shapes[id]
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            if (drag.phase != DragPhase.IDLE) return@awaitEachGesture
                            down.consume()
                            val trayCell = geo.pitch * 0.55f
                            drag.trayCell = trayCell
                            drag.trayX = origin.x + (size.width - shape.cols * trayCell) / 2f + slide[slot].value * size.width * 3f
                            drag.trayY = origin.y + (size.height - shape.rows * trayCell) / 2f
                            gestures.grab(slot, origin.x + down.position.x, origin.y + down.position.y)
                            var moved = false
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                gestures.move(origin.x + ch.position.x, origin.y + ch.position.y)
                            }
                            gestures.release(!moved)
                        }
                    },
            ) {
                if (id >= 0 && drag.slot != slot) {
                    val shape = Shapes[id]
                    val glow = selected == slot || hint?.slot == slot
                    val fits = slot in playable
                    Canvas(Modifier.fillMaxSize().graphicsLayer { translationX = slide[slot].value * size.width * 3f }) {
                        val c = geo.pitch * 0.55f
                        val ox = (size.width - shape.cols * c) / 2f
                        val oy = (size.height - shape.rows * c) / 2f
                        if (glow) {
                            drawRoundRect(
                                Wood.glow.copy(alpha = 0.25f + 0.25f * pulse),
                                Offset(ox - c * 0.35f, oy - c * 0.35f), Size(shape.cols * c + c * 0.7f, shape.rows * c + c * 0.7f),
                                CornerRadius(c * 0.4f),
                            )
                        }
                        for (p in shape.cells) {
                            val inset = c * 0.05f
                            woodBlock(ox + p.c * c + inset, oy + p.r * c + inset, c - inset * 2, alpha = if (fits) 1f else 0.38f)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- bottom bar & end panel

@Composable
private fun BottomBar(vm: GameViewModel, g: GameState) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    ) {
        if (g.mode == Mode.DAILY) {
            // Two taps, so a stray touch doesn't throw away progress.
            var armed by remember(g.moves) { mutableStateOf(false) }
            SecondaryButton(if (armed) "Tap again" else "Restart", Modifier.width(150.dp), enabled = !g.over) {
                if (armed) vm.playDaily(java.time.LocalDate.parse(g.date), restart = true) else armed = true
            }
        }
        SecondaryButton(if (vm.thinking) "Thinking…" else "Hint", Modifier.width(150.dp), enabled = !g.over && !vm.thinking) { vm.requestHint() }
    }
}

@Composable
private fun EndPanel(vm: GameViewModel, g: GameState) {
    var show by remember(g.over, g.moves) { mutableStateOf(false) }
    LaunchedEffect(g.over, g.moves) {
        if (!g.over) return@LaunchedEffect
        // Let him see the board first: the last clear, or that nothing fits.
        val fresh = vm.fx?.let { System.currentTimeMillis() - it.time < 2000 } == true
        if (fresh) delay(if (g.won) 900 else 1300)
        show = true
    }
    if (!show) return
    if (g.mode == Mode.CLASSIC) {
        val record = vm.stats.recent.firstOrNull()?.takeIf { it.score == g.score }?.record ?: ""
        ResultPanel(if (record == "ever") "New best ever!" else if (record == "today") "New best today!" else "No more room") {
            Text("${g.score}", color = P.text, fontSize = 56.sp, fontWeight = FontWeight.Bold)
            Text("points", color = P.textDim, fontSize = 16.sp)
            Spacer(Modifier.height(14.dp))
            val d = vm.today()
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MiniStat("Today", vm.highScores.todayBest(d))
                MiniStat("This week", vm.highScores.weekBest(d))
                MiniStat("Best ever", vm.highScores.ever)
            }
            Spacer(Modifier.height(10.dp))
            Text("${g.cellsCleared} blocks cleared\nBiggest combo ${g.bestCombo}", color = P.textDim, fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Menu", Modifier.weight(0.8f)) { vm.leaveGame() }
                PrimaryButton("Play again", Modifier.weight(1.2f)) { vm.newGame() }
            }
        }
    } else {
        val month = g.date!!.substring(0, 7)
        val wins = vm.daily.winsIn(month)
        ResultPanel(if (g.won) "Puzzle solved!" else "No more room") {
            if (g.won) {
                Text("All ${g.gemsTotal} gems collected", color = P.text, fontSize = 18.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("$wins ${if (wins == 1) "win" else "wins"} this month", color = P.textDim, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Milestones(wins)
            } else {
                Text("${g.gemsCollected} of ${g.gemsTotal} gems collected.\nTry this puzzle again?", color = P.text, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Calendar", Modifier.weight(1f)) { vm.leaveGame() }
                if (!g.won) PrimaryButton("Try again", Modifier.weight(1f)) { vm.playDaily(java.time.LocalDate.parse(g.date), restart = true) }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", color = P.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = P.textDim, fontSize = 13.sp, maxLines = 1)
    }
}
