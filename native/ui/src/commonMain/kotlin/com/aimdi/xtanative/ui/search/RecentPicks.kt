package com.aimdi.xtanative.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.aimdi.xtanative.ui.home.LocalDesktopActions

/**
 * How ↓ and ↑ move the highlight through the recent searches shown under a
 * search field, on the desktop. The highlight stops at the last search
 * (it does not wrap): the searches sit in a row that scrolls sideways, and
 * jumping from its far end back to the start would hide where the reader
 * is. ↑ from the first search drops the highlight, back to what is typed,
 * as a browser's address bar does.
 */
object RecentPicks {
    /**
     * The search highlighted after [by] (+1 for ↓, -1 for ↑) from [current]
     * (null: none, the typed text) among [count]; null when none is.
     */
    fun moved(current: Int?, count: Int, by: Int): Int? {
        if (count <= 0) return null
        val from = current?.coerceAtMost(count - 1)
        return when {
            by > 0 -> if (from == null) 0 else (from + 1).coerceAtMost(count - 1)
            by < 0 -> if (from == null || from == 0) null else from - 1
            else -> from
        }
    }

    /** Whether ↓ ([by] +1) or ↑ is the list's rather than the field's: ↑ with nothing highlighted still moves the caret. */
    fun takes(current: Int?, count: Int, by: Int): Boolean = count > 0 && (by > 0 || current != null)
}

/** The recent search highlighted under a field ([pickedFromRecent]); null while none is. */
@Stable
class RecentPick {
    var index: Int? by mutableStateOf(null)
        internal set

    /** The highlighted one of [queries], if it is still among them. */
    fun of(queries: List<String>): Int? = index?.takeIf { it in queries.indices }
}

/** A [RecentPick] kept with the screen. */
@Composable
fun rememberRecentPick(): RecentPick = remember { RecentPick() }

/**
 * For a search field over a [RecentSearchesBar] showing [queries] (empty
 * while it is hidden): on the desktop, while the field has the keyboard,
 * ↓ and ↑ move [pick] through them, Enter searches the highlighted one with
 * [onSelect] (else the field searches what is typed, as before), and the
 * first Esc drops the highlight before the next leaves the field. Typing
 * drops it too ([text] is what the field holds). Nothing off the desktop.
 */
@Composable
fun Modifier.pickedFromRecent(pick: RecentPick, queries: List<String>, text: String, onSelect: (String) -> Unit): Modifier {
    val actions = LocalDesktopActions.current ?: return this
    val latestQueries by rememberUpdatedState(queries)
    val latestSelect by rememberUpdatedState(onSelect)
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(text) { pick.index = null }
    DisposableEffect(actions, pick) {
        val owner = Any()
        actions.addFieldEscape(owner) {
            val dropped = focused && pick.of(latestQueries) != null
            if (dropped) pick.index = null
            dropped
        }
        onDispose { actions.removeFieldEscape(owner) }
    }
    return onFocusChanged { state ->
        focused = state.hasFocus
        if (!state.hasFocus) pick.index = null
    }.onPreviewKeyEvent { event ->
        if (event.isAltPressed || event.isCtrlPressed || event.isShiftPressed) return@onPreviewKeyEvent false
        val shown = latestQueries
        val current = pick.of(shown)
        val by = when (event.key) {
            Key.DirectionDown -> 1
            Key.DirectionUp -> -1
            else -> 0
        }
        when {
            by != 0 && RecentPicks.takes(current, shown.size, by) -> {
                if (event.type == KeyEventType.KeyDown) pick.index = RecentPicks.moved(current, shown.size, by)
                true
            }
            (event.key == Key.Enter || event.key == Key.NumPadEnter) && current != null -> {
                if (event.type == KeyEventType.KeyDown) {
                    pick.index = null
                    latestSelect(shown[current])
                }
                true
            }
            else -> false
        }
    }
}
