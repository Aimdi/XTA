package com.aimdi.xtanative.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Where Alt+↑ and Alt+↓ take a row of a list otherwise reordered by dragging. */
object RowSteps {
    /**
     * The place the row at [index] moves to, a place up or down, staying
     * within [within] (the rows it may be dragged among); null at an end,
     * where nothing happens.
     */
    fun target(index: Int, up: Boolean, within: IntRange): Int? {
        if (index !in within) return null
        val next = if (up) index - 1 else index + 1
        return next.takeIf { it in within }
    }

    /**
     * The move Alt+↑ or Alt+↓ makes of the row of [key] among [keys] as they
     * are at that moment, (from, to); null when the row is gone or at an end.
     * Taking the row by its key, not by a place read earlier, keeps a held key
     * moving the same row along while the list catches up with each move.
     */
    fun <K> step(keys: List<K>, key: K, up: Boolean): Pair<Int, Int>? {
        val from = keys.indexOf(key)
        return target(from, up, keys.indices)?.let { to -> from to to }
    }

    /** Up for Alt+↑, down (false) for Alt+↓, null for any other key or with Ctrl, Shift or Meta held too. */
    fun direction(key: Key, alt: Boolean, ctrl: Boolean, shift: Boolean, meta: Boolean): Boolean? = when {
        !alt || ctrl || shift || meta -> null
        key == Key.DirectionUp -> true
        key == Key.DirectionDown -> false
        else -> null
    }
}

/**
 * A reorderable list's rows as Alt+↑ and Alt+↓ move them: the row with the
 * focus (a button on it reached with Tab), else the one the pointer rests on
 * ([hovered], through the window's keys). [move] moves the row of that key a
 * place up or down by the list's own save, answering false at an end.
 */
@Stable
class RowKeyMoves internal constructor(private val move: (key: Any, up: Boolean) -> Boolean) {
    /** The row the pointer rests on, the one the window's keys move. */
    internal var hovered: Any? = null
        private set

    /** Where in the window the pointer last was over a row; null when unknown. */
    private var pointer: Offset? = null

    /**
     * A row was just moved from under a still pointer: the row now under it
     * is not the one to move next, so the Enter and Move the window sends as
     * the rows change places under the pointer are not heard until it really
     * moves ([DesktopKeys.pointerMoved]), and Alt+↑ held climbs one row along.
     */
    private var held = false

    /** The pointer over the row of [key], at [at] in the window. */
    internal fun pointed(key: Any, at: Offset?) {
        if (still(at)) return
        hovered = key
    }

    /** The pointer off the row of [key], at [at] in the window. */
    internal fun left(key: Any, at: Offset?) {
        if (still(at)) return
        if (hovered == key) hovered = null
    }

    /** Whether an event at [at] comes from a pointer standing still since a row moved from under it; otherwise [at] is where it now is. */
    private fun still(at: Offset?): Boolean {
        if (held && !DesktopKeys.pointerMoved(pointer, at)) return true
        held = false
        pointer = at
        return false
    }

    internal fun moveHovered(up: Boolean): Boolean {
        val key = hovered ?: return false
        return move(key, up).also { moved -> if (moved) held = true }
    }

    internal fun moveRow(key: Any, up: Boolean): Boolean = move(key, up)
}

/**
 * The keys for a reorderable list while it is shown: [move] moves the row of
 * a key a place up or down, as a drag of it would, and answers whether it
 * did. Off the desktop only a row with the focus moves (a keyboard plugged into a tablet).
 */
@Composable
fun rememberRowKeyMoves(move: (key: Any, up: Boolean) -> Boolean): RowKeyMoves {
    val latest by rememberUpdatedState(move)
    val moves = remember { RowKeyMoves { key, up -> latest(key, up) } }
    val actions = LocalDesktopActions.current ?: return moves
    val owner = remember { Any() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { actions.addRowMover(owner, moves::moveHovered) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { actions.removeRowMover(owner) }
    DisposableEffect(actions) { onDispose { actions.removeRowMover(owner) } }
    return moves
}

/**
 * The row of [key] in a list of [moves]: Alt+↑ and Alt+↓ move it while it,
 * or something on it, has the focus, and through the window's keys while the
 * pointer rests on it. A text field never sits on such a row.
 */
@Composable
fun Modifier.rowMovedByKeys(moves: RowKeyMoves, key: Any): Modifier {
    // Where the row stands in the window, so a still pointer is known as still while the rows move under it.
    val place = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return onGloballyPositioned { place[0] = it }.pointerInput(moves, key) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val at = event.changes.firstOrNull()?.let { change -> place[0]?.takeIf { it.isAttached }?.localToWindow(change.position) }
                when (event.type) {
                    PointerEventType.Enter, PointerEventType.Move -> moves.pointed(key, at)
                    PointerEventType.Exit -> moves.left(key, at)
                }
            }
        }
    }.onKeyEvent { event -> event.type == KeyEventType.KeyDown && moveFor(event, moves, key) }
}

/** Alt+↑ or Alt+↓ on the row of [key] with the focus: taken even at an end, so it never falls through to the row under the pointer. */
private fun moveFor(event: KeyEvent, moves: RowKeyMoves, key: Any): Boolean =
    RowSteps.direction(event.key, event.isAltPressed, event.isCtrlPressed, event.isShiftPressed, event.isMetaPressed)
        ?.let { up -> moves.moveRow(key, up); true }
        ?: false

/**
 * The whole of a list of [moves] where the window's keys do not reach (a
 * sheet): Alt+↑ and Alt+↓ move the row the pointer rests on while the focus
 * is anywhere in it, a row with the focus answering first.
 */
fun Modifier.hoveredRowMovedByKeys(moves: RowKeyMoves): Modifier =
    onKeyEvent { event ->
        event.type == KeyEventType.KeyDown &&
            RowSteps.direction(event.key, event.isAltPressed, event.isCtrlPressed, event.isShiftPressed, event.isMetaPressed)?.let(moves::moveHovered) == true
    }
