package com.aimdi.xtanative.ui.chrome

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.settings.AccessibilityPrefs
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.home.DesktopShortcuts
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * An opened post as XTA opens one (card_page_route.dart): it slides in over
 * [behind], the page it was opened from, which shrinks and darkens under
 * it, and goes back as a card the reader drags away by finger or mouse,
 * throws, or lets drop back; Android's back gesture moves it too, and Back
 * slides it out. Settings › Accessibility says whether it is dragged from
 * anywhere or only the edge, and whether it tilts; reduced motion moves it
 * flat and at once. [leave] takes its screen away, once, when it has gone;
 * [content] is handed the back that slides it out. Nothing moves when there
 * is nothing to go back to ([canLeave] false).
 */
@Composable
fun SwipeBackCard(behind: ImageBitmap?, canLeave: Boolean, leave: () -> Unit, content: @Composable (back: () -> Unit) -> Unit) {
    val settings by LocalGraph.current.settings.state.collectAsStateWithLifecycle()
    val reducedSetting = LocalReducedMotion.current
    var systemStill by remember { mutableStateOf(false) }
    var arrived by rememberSaveable { mutableStateOf(false) }
    val card = remember { CardController(CardMotion().also { if (arrived) it.rest() }) }
    card.reduced = reducedSetting || systemStill
    card.anywhere = settings?.get(AccessibilityPrefs.swipeBackAnywhere) ?: true
    card.tilt = settings?.get(AccessibilityPrefs.swipeBackTilt) ?: true
    card.rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    card.density = LocalDensity.current.density
    val latestLeave by rememberUpdatedState(leave)
    val back = remember(card) { { card.back() } }

    LaunchedEffect(card) {
        // Android's "Remove animations" stills Compose's own animations through this scale: the card too, as XTA's reduced motion.
        val still = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f
        systemStill = still
        card.reduced = card.reduced || still
        if (!arrived) {
            // With no picture of the page it came from, it does not slide in over the bare stage.
            card.motion.arrive(animate = canLeave && !card.reduced && behind != null)
            arrived = true
            card.wake()
        }
        card.drive { latestLeave() }
    }
    if (canLeave) {
        CardBackGesture(enabled = true, events = card.backEvents)
        DesktopShortcuts(refresh = {}, turn = {}, isOpen = { true }, close = back)
    }
    card.gestures = gestureEdges()
    val page = MaterialTheme.colorScheme.background.luminance()
    val dark = page < 0.5f
    // Behind a dark page that is not black the stage is black, so the page stands out from it.
    val stage = if (dark && page > 0.005f) Color.Black else NEAR_BLACK
    Box(Modifier.fillMaxSize().drawBehind { drawBehindCard(card, behind, stage) }) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (canLeave) Modifier.pointerInput(card) { cardDrags(card) } else Modifier)
                .cardPose(card, shadow = true),
        ) { content(back) }
        if (dark) Box(Modifier.fillMaxSize().cardPose(card, shadow = false).drawBehind { drawEdge(card) })
    }
}

/** Android's own gesture edges, in dp: a swipe that starts there is its back gesture. */
@Composable
private fun gestureEdges(): GestureEdges {
    val insets = WindowInsets.systemGestures
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    return GestureEdges(
        left = insets.getLeft(density, direction) / density.density,
        top = insets.getTop(density) / density.density,
        right = insets.getRight(density, direction) / density.density,
        bottom = insets.getBottom(density) / density.density,
    )
}

private fun DrawScope.drawBehindCard(card: CardController, behind: ImageBitmap?, stage: Color) {
    card.frame.intValue
    val pose = card.pose(size.width, size.height)
    if (pose.offset == CardVec.ZERO && pose.lift == 0f && pose.rotation == 0f) return
    drawRect(stage)
    val image = behind ?: return
    val look = SwipeBack.behindLook(pose.uncovered)
    scale(look.scale, pivot = center) {
        val outline = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(look.radius * density))) }
        clipPath(outline) {
            drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            if (look.dim > 0f) drawRect(Color.Black.copy(alpha = look.dim))
        }
    }
}

/** The faint light edge of a card lifted off a dark page: a black card over a black page would not show at all. */
private fun DrawScope.drawEdge(card: CardController) {
    card.frame.intValue
    val lift = card.pose(size.width, size.height).lift
    if (lift <= 0f) return
    val stroke = 1.dp.toPx()
    drawRoundRect(
        DARK_EDGE.copy(alpha = DARK_EDGE.alpha * lift),
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(SwipeBack.cardRadius(lift) * density),
        style = Stroke(stroke),
    )
}

/** The card at its pose: moved, turned and shrunk around where it was grabbed, its corners rounded and a shadow under it as it lifts. */
private fun Modifier.cardPose(card: CardController, shadow: Boolean): Modifier = graphicsLayer {
    card.frame.intValue
    val pose = card.pose(size.width, size.height)
    val width = size.width / density
    val height = size.height / density
    val side = if (pose.mirrored) -1f else 1f
    val origin = if (pose.mirrored) CardVec(width - pose.origin.x, pose.origin.y) else pose.origin
    val scale = SwipeBack.cardScale(pose.offset, pose.lift, CardSize(width, height))
    transformOrigin = TransformOrigin(origin.x / width, origin.y / height)
    translationX = side * pose.offset.x * density
    translationY = pose.offset.y * density
    rotationZ = side * pose.rotation
    scaleX = scale
    scaleY = scale
    shape = RoundedCornerShape(SwipeBack.cardRadius(pose.lift).dp)
    clip = pose.lift > 0f
    shadowElevation = if (shadow) pose.lift * SHADOW.toPx() else 0f
}

/** The card's state between its input, its frames and its drawing; lengths in dp, times from [now]. */
private class CardController(val motion: CardMotion) {
    var reduced = false
    var anywhere = true
    var tilt = true
    var rtl = false
    var density = 1f
    var gestures = GestureEdges.NONE

    /** Bumped every frame the card moves, for its drawing to follow. */
    val frame = mutableIntStateOf(0)
    private val running = mutableStateOf(false)
    private var size = CardSize(1f, 1f)

    fun pose(widthPx: Float, heightPx: Float): CardPose {
        size = CardSize(widthPx / density, heightPx / density)
        return motion.pose(size, rtl)
    }

    fun sizeOf(widthPx: Int, heightPx: Int): CardSize = CardSize(widthPx / density, heightPx / density)

    /** Frames are needed. */
    fun wake() {
        running.value = true
    }

    /** Ticks the card every frame while it moves; [gone] once it has left. */
    suspend fun drive(gone: () -> Unit) {
        while (true) {
            snapshotFlow { running.value }.first { it }
            while (motion.moving && !motion.gone) {
                withFrameNanos {
                    motion.tick(now())
                    frame.intValue++
                }
            }
            running.value = false
            frame.intValue++
            if (motion.gone) return gone()
        }
    }

    fun back() {
        motion.back(now(), animate = !reduced)
        wake()
    }

    fun grab(at: CardVec, downAt: Double, size: CardSize) {
        motion.hand.grab(at, size, mirrored = rtl, tilt = tilt, flat = reduced, now = now(), pointer = true, downAt = downAt)
        wake()
    }

    fun move(at: CardVec) = motion.hand.move(at, now())

    fun release() = settled(motion.hand.release(now()))

    fun cancel() = settled(motion.hand.cancel(now()))

    private fun settled(result: CardRelease?) {
        motion.released(result)
        frame.intValue++
        wake()
    }

    val backEvents = object : CardBackEvents {
        override fun started(x: Float, y: Float, fromRight: Boolean) {
            // With reduced motion Android's gesture is left as it is: Back on release.
            if (reduced || !motion.mayTake(catching = false)) return
            motion.hand.grab(CardVec(x / density, y / density), size, mirrored = fromRight, tilt = tilt, flat = false, now = now(), pointer = false)
            wake()
        }

        override fun progressed(progress: Float) = motion.hand.follow(progress)

        override fun committed() {
            if (motion.hand.busy) settled(motion.hand.leave(now())) else back()
        }

        override fun cancelled() = settled(motion.hand.drop(now()))

        override fun pressed() = back()
    }
}

/**
 * Takes a drag that heads back (XTA's SwipeBackDragRecognizer): once it has
 * gone [SwipeBack.SLOP] it must go across, more than up or down, and it is
 * the card's past the touch slop, after the post's own sideways drags (a
 * row of photos, a seek bar) have had it first. Then nothing in the post
 * takes the pointer until it is let go. A pointer going down while the
 * card drops back catches it at once.
 */
private suspend fun PointerInputScope.cardDrags(card: CardController) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
        val motion = card.motion
        if (motion.leaving) return@awaitEachGesture swallow(down.id)
        val at = CardVec(down.position.x / density, down.position.y / density)
        val area = card.sizeOf(size.width, size.height)
        val catching = motion.hand.catchable
        val starts = motion.mayTake(catching = true) && CardDrags.mayStart(at, area, card.rtl, card.anywhere, catching, card.gestures)
        if (!starts) return@awaitEachGesture
        val downAt = now()
        if (catching) {
            down.consume()
            card.grab(at, downAt, area)
        } else {
            val slop = if (down.type == PointerType.Mouse) 1f else viewConfiguration.touchSlop / density
            if (!headsBack(down.id, at, slop, card.rtl) { position -> card.grab(at, downAt, area); card.move(position) }) return@awaitEachGesture
        }
        hold(down.id, card)
    }
}

/**
 * Whether the pointer heads back before anything in the post takes it,
 * read after the post had its turn; [take] is handed where it is then.
 */
private suspend fun AwaitPointerEventScope.headsBack(id: PointerId, at: CardVec, slop: Float, rtl: Boolean, take: (CardVec) -> Unit): Boolean {
    var aimed = false
    val direction = if (rtl) -1f else 1f
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull { it.id == id } ?: return false
        if (change.changedToDownIgnoreConsumed()) continue
        if (!change.pressed || change.isConsumed) return false
        val position = CardVec(change.position.x / density, change.position.y / density)
        val (drag, nowAimed) = CardDrags.consider(position - at, aimed, direction, slop)
        aimed = nowAimed
        when (drag) {
            CardDrag.WAIT -> Unit
            CardDrag.LEAVE -> return false
            CardDrag.TAKE -> {
                change.consume()
                take(position)
                return true
            }
        }
    }
}

/** The card follows the pointer until it is let go, taking every move before the post sees it. */
private suspend fun AwaitPointerEventScope.hold(id: PointerId, card: CardController) {
    try {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == id }
            event.changes.forEach { it.consume() }
            if (change == null) return card.cancel()
            if (change.changedToUpIgnoreConsumed() || !change.pressed) return card.release()
            card.move(CardVec(change.position.x / density, change.position.y / density))
        }
    } catch (e: CancellationException) {
        card.cancel()
        throw e
    }
}

/** A card on its way out takes no taps. */
private suspend fun AwaitPointerEventScope.swallow(id: PointerId) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
        if (event.changes.none { it.id == id && it.pressed }) return
    }
}

/** One clock for the pointer and the frames, in milliseconds. */
private fun now(): Double = System.nanoTime() / 1_000_000.0

/** The stage behind a light page, or a black one. */
private val NEAR_BLACK = Color(0xFF16181D)

private val DARK_EDGE = Color(0x24FFFFFF)

private val SHADOW = 16.dp
