package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * The lists a sheet shows ([com.aimdi.xtanative.ui.common.KeyedSheet]),
 * which the desktop's reading keys move while it is open: a sheet is drawn
 * over the window, apart from its frame, and the window's keys never reach
 * it. A list in it marked [movedByKeys] (or [verticalScrollByKeys]) is
 * listed here rather than with the window's.
 */
class SheetKeys {
    private val lists = mutableListOf<ScrollableState>()
    private var pointed: ScrollableState? = null

    internal fun add(list: ScrollableState) {
        lists += list
    }

    internal fun remove(list: ScrollableState) {
        lists -= list
        if (pointed === list) pointed = null
    }

    internal fun point(list: ScrollableState) {
        pointed = list
    }

    /** The list the keys move: the one the pointer was last over, else the first shown. */
    internal fun list(): ScrollableState? = pointed ?: lists.firstOrNull()

    /** The sheet's own focus (it is drawn apart from the window's), which ↑ and ↓ move along its rows where it has no list. */
    internal var focus: FocusManager? = null
}

/** The sheet what is drawn here is in ([SheetKeys]); null outside one. */
val LocalSheetKeys = staticCompositionLocalOf<SheetKeys?> { null }

/** A new sheet's lists, for a sheet of its own. */
@Composable
fun rememberSheetKeys(): SheetKeys = remember { SheetKeys() }

/**
 * The sheet itself ([SheetKeys]) answering the desktop's reading keys: ↑ ↓,
 * Page Up and Down, Space (with Shift, up), Home and End move its list, as
 * the window's do its page, unless what has the focus in it used the key (a
 * field typing, a slider). It takes the keys as it opens, unless something
 * in it (its search field) took them first. Nothing off the desktop.
 */
@Composable
internal expect fun Modifier.sheetKeys(keys: SheetKeys): Modifier

/**
 * A lazy list's state that, in a sheet ([SheetKeys]), the desktop's keys
 * move; a plain one elsewhere. For a list that takes no [movedByKeys].
 */
@Composable
fun rememberSheetList(): LazyListState {
    val list = rememberLazyListState()
    val keys = LocalSheetKeys.current ?: return list
    DisposableEffect(keys, list) {
        keys.add(list)
        onDispose { keys.remove(list) }
    }
    return list
}

/** [list] in [keys]'s sheet, the one the keys move while the pointer is over it. */
@Composable
internal fun Modifier.movedInSheet(keys: SheetKeys, list: ScrollableState): Modifier {
    DisposableEffect(keys, list) {
        keys.add(list)
        onDispose { keys.remove(list) }
    }
    return pointerInput(keys, list) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Enter || event.type == PointerEventType.Move) keys.point(list)
            }
        }
    }
}
