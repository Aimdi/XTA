package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import com.aimdi.xtanative.ui.home.EDGE_BAND
import com.aimdi.xtanative.ui.home.EDGE_SPEED
import com.aimdi.xtanative.ui.home.DragSlots
import com.aimdi.xtanative.ui.home.ReorderDrags
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Dragging one row of a lazy list to a new place, as XTA's reorderable lists
 * do: the row follows the finger, the others make room, and the move is
 * handed over once, when the finger lifts. [order] is the keys as shown
 * while dragging; the list's own data changes only then.
 */
@Stable
class RowDrag(private val list: LazyListState) {
    var key by mutableStateOf<Any?>(null)
        private set

    /** How far the dragged row is from its slot, in pixels. */
    var offset by mutableFloatStateOf(0f)
        private set

    var order by mutableStateOf<List<Any>>(emptyList())
        private set

    private var from = -1

    private val stop: () -> Unit = { cancel() }

    /** Starts dragging [itemKey], one of [keys]; Esc puts it back ([ReorderDrags]). */
    fun start(itemKey: Any, keys: List<Any>) {
        order = keys
        from = keys.indexOf(itemKey)
        key = itemKey.takeIf { from >= 0 }
        offset = 0f
        if (key != null) ReorderDrags.began(stop)
    }

    /** Put back where it was, as at Esc: the drag goes on unheard until let go. */
    fun cancel() {
        key = null
        offset = 0f
        from = -1
        ReorderDrags.ended(stop)
    }

    /** The finger moved by [dy]: past a neighbour's middle, the dragged row takes its slot. */
    fun drag(dy: Float) {
        val dragged = key ?: return
        offset += dy
        val visible = list.layoutInfo.visibleItemsInfo
        val item = visible.firstOrNull { it.key == dragged } ?: return
        val middle = item.offset + offset + item.size / 2f
        val target = visible.firstOrNull { it.key != dragged && it.key in order && middle >= it.offset && middle <= it.offset + it.size } ?: return
        val at = order.indexOf(target.key)
        order = SubscriptionList.moved(order, order.indexOf(dragged), at)
        offset += item.offset - target.offset
    }

    /** The finger lifted: the move from where the row was to where it is now, or null for none. */
    fun end(): Pair<Int, Int>? {
        val dragged = key ?: return null
        val to = order.indexOf(dragged)
        val move = (from to to).takeIf { from >= 0 && to >= 0 && from != to }
        cancel()
        return move
    }

    /**
     * While a row is dragged, the list scrolls when it is dragged into the
     * [band] at an edge, up to [max] pixels a frame, the row staying under
     * the pointer and taking the places it passes.
     */
    internal suspend fun scrollAtEdges(band: Float, max: Float) {
        while (key != null) {
            withFrameNanos { }
            val dragged = key ?: break
            drag(0f)
            val info = list.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == dragged } ?: continue
            val top = item.offset + offset
            val step = DragSlots.edgeStep(top, top + item.size, info.viewportStartOffset.toFloat(), info.viewportEndOffset.toFloat(), band, max, offset)
            if (step != 0f) offset += list.scrollBy(step)
        }
    }

    /** The keys in the order to show: while dragging the moved order, else [keys] as they are. */
    fun shown(keys: List<Any>): List<Any> = if (key != null) order else keys
}

@Composable
fun rememberRowDrag(list: LazyListState): RowDrag {
    val drag = remember(list) { RowDrag(list) }
    val density = LocalDensity.current
    val dragging = drag.key != null
    LaunchedEffect(drag, dragging) {
        if (dragging) with(density) { drag.scrollAtEdges(EDGE_BAND.toPx(), EDGE_SPEED.toPx()) }
    }
    return drag
}
