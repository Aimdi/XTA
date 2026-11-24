package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.coroutines.cancellation.CancellationException

/** Where a dragged item of a row or column of items of known sizes lands, and how the page scrolls under it near an edge. */
object DragSlots {
    /**
     * Where an item dragged from [from] by [offset] pixels lands among items
     * of [sizes]: past each neighbour once more than half of it is crossed.
     */
    fun drop(from: Int, offset: Float, sizes: List<Int>): Int {
        if (from !in sizes.indices) return from
        var target = from
        var left = kotlin.math.abs(offset)
        val step = if (offset > 0) 1 else -1
        var next = from + step
        while (offset != 0f && next in sizes.indices && left > sizes[next] / 2f) {
            left -= sizes[next]
            target = next
            next += step
        }
        return target
    }

    /** How far the item at [index] makes way for the one dragged from [from] to [to]: by that one's size, the other way. */
    fun shift(index: Int, from: Int?, to: Int?, sizes: List<Int>): Float {
        if (from == null || to == null || index == from) return 0f
        val moved = sizes.getOrElse(from) { 0 }.toFloat()
        return when {
            from < to && index in (from + 1)..to -> -moved
            from > to && index in to until from -> moved
            else -> 0f
        }
    }

    /**
     * How far to scroll this frame while what is dragged spans [lead]..[trail]
     * in a view from [start] to [end]: faster the deeper it reaches into the
     * [band] at an edge, up to [max]; only the way it was dragged ([moving],
     * by sign), so one picked up near an edge does not run off at once; and
     * never in a view too short to have two bands.
     */
    fun edgeStep(lead: Float, trail: Float, start: Float, end: Float, band: Float, max: Float, moving: Float): Float {
        if (band <= 0f || end - start <= band * 2) return 0f
        val before = start + band - lead
        val after = trail - (end - band)
        val step = when {
            before > 0f && after > 0f -> 0f
            before > 0f -> -max * (before / band).coerceAtMost(1f)
            after > 0f -> max * (after / band).coerceAtMost(1f)
            else -> 0f
        }
        return step.takeIf { (it < 0f && moving < 0f) || (it > 0f && moving > 0f) } ?: 0f
    }
}

/**
 * The drag of a reorderable list's item going on now, which Esc cancels,
 * the item going back to where it was: the window's keys ask [escape] first.
 */
object ReorderDrags {
    private var cancel: (() -> Unit)? = null

    internal fun began(stop: () -> Unit) {
        cancel = stop
    }

    internal fun ended(stop: () -> Unit) {
        if (cancel === stop) cancel = null
    }

    /** Esc: true when it cancelled a drag, and is spent on that. */
    fun escape(): Boolean {
        val stop = cancel ?: return false
        cancel = null
        stop()
        return true
    }
}

/** How deep the band at a scrolling view's edge is where a dragged item scrolls it, and how fast it does at most, per frame. */
internal val EDGE_BAND = 56.dp
internal val EDGE_SPEED = 18.dp

/**
 * Dragging one of a few items in a [vertical] column or a row that is not
 * lazy (all of them laid out) to a new place, by the mouse or a finger: the
 * item follows the pointer, the others make way, and the move is handed over
 * once, when it is let go. Near an edge of the view the page [scroll]s, and
 * Esc cancels ([ReorderDrags]).
 */
@Stable
class SlotDrag internal constructor(private val scroll: ScrollState?, internal val vertical: Boolean) {
    private val sizes = mutableStateMapOf<Any, Int>()

    /** The item dragged, null when none is. */
    var key by mutableStateOf<Any?>(null)
        private set

    private var offset by mutableFloatStateOf(0f)
    private var keys: List<Any> = emptyList()
    private var pointer: Float? = null
    private var view: Pair<Float, Float>? = null
    private val stop: () -> Unit = { cancel() }

    private val from: Int? get() = key?.let { dragged -> keys.indexOf(dragged).takeIf { it >= 0 } }
    private val shownSizes: List<Int> get() = keys.map { sizes[it] ?: 0 }
    private val target: Int? get() = from?.let { DragSlots.drop(it, offset, shownSizes) }

    /** How far the item of [itemKey] is drawn from its place: the dragged one with the pointer, the others making way. */
    fun translation(itemKey: Any): Float {
        val start = from ?: return 0f
        if (itemKey == key) return offset
        return DragSlots.shift(keys.indexOf(itemKey), start, target, shownSizes)
    }

    internal fun along(at: Offset): Float = if (vertical) at.y else at.x

    internal fun sized(itemKey: Any, size: Int) {
        sizes[itemKey] = size
    }

    internal fun viewport(start: Float, end: Float) {
        view = start to end
    }

    /** Picks up the item of [itemKey], one of [all] in the order shown, the pointer at [at] in the window. */
    fun start(itemKey: Any, all: List<Any>, at: Float?) {
        if (itemKey !in all) return
        keys = all
        key = itemKey
        offset = 0f
        pointer = at
        ReorderDrags.began(stop)
    }

    /** The pointer moved by [by], to [at] in the window. */
    fun moveBy(by: Float, at: Float?) {
        if (key == null) return
        offset += by
        pointer = at
    }

    /** Let go: the move from where the item was to where it lands, null for none. */
    fun end(): Pair<Int, Int>? {
        val start = from
        val to = target
        clear()
        return if (start != null && to != null && start != to) start to to else null
    }

    /** Put back where it was, as at Esc. */
    fun cancel() = clear()

    private fun clear() {
        key = null
        offset = 0f
        pointer = null
        ReorderDrags.ended(stop)
    }

    internal suspend fun scrollAtEdges(band: Float, max: Float) {
        val page = scroll ?: return
        while (key != null) {
            withFrameNanos { }
            val at = pointer ?: continue
            val (start, end) = view ?: continue
            val step = DragSlots.edgeStep(at, at, start, end, band, max, offset)
            if (step != 0f) offset += page.scrollBy(step)
        }
    }
}

/** A [SlotDrag] for items laid out in a column ([vertical]) or a row scrolled by [scroll]. */
@Composable
fun rememberSlotDrag(scroll: ScrollState?, vertical: Boolean): SlotDrag {
    val drag = remember(scroll, vertical) { SlotDrag(scroll, vertical) }
    val density = LocalDensity.current
    val dragging = drag.key != null
    LaunchedEffect(drag, dragging) {
        if (dragging) with(density) { drag.scrollAtEdges(EDGE_BAND.toPx(), EDGE_SPEED.toPx()) }
    }
    return drag
}

/** The view [drag]'s items scroll in (put before its scroll), whose edges scroll it while an item is dragged near them. */
fun Modifier.slotViewport(drag: SlotDrag): Modifier = onGloballyPositioned { place ->
    val bounds = place.boundsInWindow()
    if (drag.vertical) drag.viewport(bounds.top, bounds.bottom) else drag.viewport(bounds.left, bounds.right)
}

/**
 * One item of [drag], of [key]: drawn where the drag puts it, the dragged one
 * over the rest, lifted and ringed in [outline] while the others make way.
 */
fun Modifier.dragSlot(drag: SlotDrag, key: Any, outline: Color = Color.Unspecified): Modifier =
    onSizeChanged { drag.sized(key, drag.along(Offset(it.width.toFloat(), it.height.toFloat())).toInt()) }
        .zIndex(if (drag.key == key) 1f else 0f)
        .graphicsLayer {
            val by = drag.translation(key)
            if (drag.vertical) translationY = by else translationX = by
            shadowElevation = if (drag.key == key) 8.dp.toPx() else 0f
        }
        .drawWithContent {
            drawContent()
            if (drag.key == key && outline.isSpecified) {
                val width = 2.dp.toPx()
                drawRect(outline, topLeft = Offset(width / 2, width / 2), size = Size(size.width - width, size.height - width), style = Stroke(width))
            }
        }

/**
 * Where the item of [key] among [keys] (in the order shown) is picked up to
 * drag it with [drag]: by any pointer, or with [mouseOnly] by the mouse alone
 * (a finger scrolls the page there, or long-presses). [onMoved] hears where it
 * was let go.
 */
@Composable
fun Modifier.slotDragHandle(drag: SlotDrag, key: Any, keys: List<Any>, mouseOnly: Boolean, onMoved: (from: Int, to: Int) -> Unit): Modifier {
    val all by rememberUpdatedState(keys)
    val moved by rememberUpdatedState(onMoved)
    return pressDrag(
        key = key,
        mouseOnly = mouseOnly,
        onStart = { at -> drag.start(key, all, at?.let(drag::along)) },
        onMove = { by, at -> drag.moveBy(drag.along(by), at?.let(drag::along)) },
        onEnd = { done -> if (done) drag.end()?.let { (from, to) -> moved(from, to) } else drag.cancel() },
    )
}

/**
 * A press and drag past the touch slop, with the primary button for a mouse:
 * [onStart] at the pointer's place in the window, [onMove] by each step, and
 * [onEnd] when let go (true) or cancelled (false). With [mouseOnly] a finger
 * or pen is left to what else is under it. A click with no drag stays a click.
 */
@Composable
fun Modifier.pressDrag(
    key: Any?,
    mouseOnly: Boolean,
    onStart: (at: Offset?) -> Unit,
    onMove: (by: Offset, at: Offset?) -> Unit,
    onEnd: (done: Boolean) -> Unit,
): Modifier {
    val place = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val start by rememberUpdatedState(onStart)
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    return onGloballyPositioned { place[0] = it }.pointerInput(key, mouseOnly) {
        fun window(at: Offset): Offset? = place[0]?.takeIf { it.isAttached }?.localToWindow(at)
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val mouse = down.type == PointerType.Mouse
            if ((mouseOnly && !mouse) || (mouse && !currentEvent.buttons.isPrimaryPressed)) return@awaitEachGesture
            val past = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
            var started = false
            try {
                start(window(past.position))
                started = true
                move(past.position - down.position, window(past.position))
                val done = drag(past.id) { change ->
                    move(change.positionChange(), window(change.position))
                    change.consume()
                }
                end(done)
            } catch (e: CancellationException) {
                if (started) end(false)
                throw e
            }
        }
    }
}
