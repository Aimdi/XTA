package com.aimdi.xtanative.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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
    internal var hovered: Any? = null

    internal fun moveHovered(up: Boolean): Boolean = hovered?.let { move(it, up) } ?: false

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
fun Modifier.rowMovedByKeys(moves: RowKeyMoves, key: Any): Modifier =
    pointerInput(moves, key) {
        awaitPointerEventScope {
            while (true) {
                when (awaitPointerEvent().type) {
                    PointerEventType.Enter, PointerEventType.Move -> moves.hovered = key
                    PointerEventType.Exit -> if (moves.hovered == key) moves.hovered = null
                }
            }
        }
    }.onKeyEvent { event -> event.type == KeyEventType.KeyDown && moveFor(event, moves, key) }

private fun moveFor(event: KeyEvent, moves: RowKeyMoves, key: Any): Boolean =
    RowSteps.direction(event.key, event.isAltPressed, event.isCtrlPressed, event.isShiftPressed, event.isMetaPressed)
        ?.let { up -> moves.moveRow(key, up) }
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
