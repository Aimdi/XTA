package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.foundation.lazy.LazyListState
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

    /** Starts dragging [itemKey], one of [keys]. */
    fun start(itemKey: Any, keys: List<Any>) {
        order = keys
        from = keys.indexOf(itemKey)
        key = itemKey.takeIf { from >= 0 }
        offset = 0f
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
        key = null
        offset = 0f
        from = -1
        return move
    }

    /** The keys in the order to show: while dragging the moved order, else [keys] as they are. */
    fun shown(keys: List<Any>): List<Any> = if (key != null) order else keys
}

@Composable
fun rememberRowDrag(list: LazyListState): RowDrag = remember(list) { RowDrag(list) }
