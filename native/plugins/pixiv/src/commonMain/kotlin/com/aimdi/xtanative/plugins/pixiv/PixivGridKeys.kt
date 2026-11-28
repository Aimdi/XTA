package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.aimdi.xtanative.ui.plugins.PluginPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a key does in a works grid on the desktop. */
internal enum class GridKey { NEXT, PREVIOUS, DOWN, UP, SCREEN_DOWN, SCREEN_UP, FIRST, LAST, OPEN, MENU, NONE }

/**
 * A works grid's keys, as the app's lists have them: j and → mark the next
 * work, k and ← the one before, ↓ and ↑ the one a row down or up, Page Down
 * and Up (and Space, with Shift back) a screen on or back, Home and End the
 * first and the last; Enter or o opens the marked work, the Menu key or
 * Shift+F10 its actions. Nothing with Ctrl, Alt or Meta held, which are the
 * app's.
 */
internal object GridKeys {
    fun of(key: Key, shift: Boolean, ctrl: Boolean, alt: Boolean): GridKey = when {
        ctrl || alt -> GridKey.NONE
        key == Key.Spacebar -> if (shift) GridKey.SCREEN_UP else GridKey.SCREEN_DOWN
        key == Key.F10 && shift -> GridKey.MENU
        shift -> GridKey.NONE
        else -> PLAIN[key] ?: GridKey.NONE
    }

    private val PLAIN = mapOf(
        Key.J to GridKey.NEXT,
        Key.DirectionRight to GridKey.NEXT,
        Key.K to GridKey.PREVIOUS,
        Key.DirectionLeft to GridKey.PREVIOUS,
        Key.DirectionDown to GridKey.DOWN,
        Key.DirectionUp to GridKey.UP,
        Key.PageDown to GridKey.SCREEN_DOWN,
        Key.PageUp to GridKey.SCREEN_UP,
        Key.MoveHome to GridKey.FIRST,
        Key.MoveEnd to GridKey.LAST,
        Key.Enter to GridKey.OPEN,
        Key.NumPadEnter to GridKey.OPEN,
        Key.O to GridKey.OPEN,
        Key.Menu to GridKey.MENU,
    )

    /**
     * The work [key] marks of [count], from the one [marked] (none yet: the
     * first on screen, [firstShown]), a row being [columns] works and a
     * screen [screen] works; null when it marks none, or the same one.
     */
    fun target(key: GridKey, marked: Int?, count: Int, columns: Int, screen: Int, firstShown: Int): Int? {
        if (count <= 0) return null
        val from = marked?.coerceIn(0, count - 1)
        val start = firstShown.coerceIn(0, count - 1)
        val next = when (key) {
            GridKey.NEXT -> from?.plus(1) ?: start
            GridKey.PREVIOUS -> from?.minus(1) ?: start
            GridKey.DOWN -> from?.plus(columns.coerceAtLeast(1)) ?: start
            GridKey.UP -> from?.minus(columns.coerceAtLeast(1)) ?: start
            GridKey.SCREEN_DOWN -> (from ?: start) + screen.coerceAtLeast(1)
            GridKey.SCREEN_UP -> (from ?: start) - screen.coerceAtLeast(1)
            GridKey.FIRST -> 0
            GridKey.LAST -> count - 1
            else -> return null
        }.coerceIn(0, count - 1)
        return next.takeIf { it != marked }
    }
}

/** The work a grid's keys marked, by id, so a list that grows or reorders keeps the mark on the same work. */
internal class GridMark(val id: MutableState<Long?> = mutableStateOf(null))

/**
 * [GridKeys] for a works grid ([grid], showing [ids] in [columns] columns):
 * the mark moves and is scrolled into view, [onOpen] opens a work and
 * [onMenu] its actions. The grid takes the keys as it is first shown when
 * [takeKeys] (not under a field the reader may be typing in) and whenever it
 * is clicked. Nothing off the desktop.
 */
@Composable
internal fun Modifier.worksGridKeys(
    grid: LazyStaggeredGridState,
    ids: List<Long>,
    columns: Int,
    mark: GridMark,
    takeKeys: Boolean,
    onOpen: (Int) -> Unit,
    onMenu: (Int) -> Unit,
): Modifier {
    if (!PluginPlatform.isDesktop) return this
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val shown = rememberUpdatedState(ids)
    val open = rememberUpdatedState(onOpen)
    val menu = rememberUpdatedState(onMenu)
    LaunchedEffect(takeKeys) {
        if (!takeKeys) return@LaunchedEffect
        // After the window's own focus settles on the new screen, which takes a frame or two.
        delay(TAKE_KEYS_DELAY_MS)
        runCatching { focus.requestFocus() }
    }
    return pointerInput(focus) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) runCatching { focus.requestFocus() }
            }
        }
    }.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || event.isMetaPressed) return@onKeyEvent false
        val key = GridKeys.of(event.key, event.isShiftPressed, event.isCtrlPressed, event.isAltPressed)
        if (key == GridKey.NONE) return@onKeyEvent false
        val works = shown.value
        val marked = mark.id.value?.let(works::indexOf)?.takeIf { it >= 0 }
        when (key) {
            GridKey.OPEN -> (marked ?: GridPlaces.firstShown(grid, works.size)).let { if (it in works.indices) open.value(it) }
            GridKey.MENU -> marked?.let { menu.value(it) }
            else -> {
                val target = GridKeys.target(key, marked, works.size, columns, GridPlaces.shownCount(grid, works.size), GridPlaces.firstShown(grid, works.size))
                    ?: return@onKeyEvent works.isNotEmpty()
                mark.id.value = works[target]
                // Home goes to the very top, the header above the works with it.
                scope.launch { if (key == GridKey.FIRST) grid.animateScrollToItem(0) else GridPlaces.reveal(grid, works.size, target) }
            }
        }
        true
    }.focusRequester(focus).focusable()
}

/** Where a works grid's works stand among its items: the header and its state before them, its footer last. */
internal object GridPlaces {
    /** The grid item of work [index], of [works] works. */
    fun itemOf(grid: LazyStaggeredGridState, works: Int, index: Int): Int = firstWorkItem(grid.layoutInfo.totalItemsCount, works) + index

    /** The first work's item among [total] items: only the footer comes after the works. */
    fun firstWorkItem(total: Int, works: Int): Int = (total - 1 - works).coerceAtLeast(0)

    /** The first work wholly on screen, else the first partly on it. */
    fun firstShown(grid: LazyStaggeredGridState, works: Int): Int {
        val info = grid.layoutInfo
        val first = firstWorkItem(info.totalItemsCount, works)
        val visible = info.visibleItemsInfo.filter { it.index - first in 0 until works }
        val whole = visible.filter { it.offset.y >= info.viewportStartOffset }.minByOrNull { it.index }
        return ((whole ?: visible.minByOrNull { it.index })?.index ?: first) - first
    }

    /** How many works a screen shows, at least one. */
    fun shownCount(grid: LazyStaggeredGridState, works: Int): Int {
        val info = grid.layoutInfo
        val first = firstWorkItem(info.totalItemsCount, works)
        return info.visibleItemsInfo.count { it.index - first in 0 until works }.coerceAtLeast(1)
    }

    /** Scrolls the grid just far enough that work [index] is wholly on screen. */
    suspend fun reveal(grid: LazyStaggeredGridState, works: Int, index: Int) {
        val item = itemOf(grid, works, index)
        val info = grid.layoutInfo
        val seen = info.visibleItemsInfo.firstOrNull { it.index == item }
        if (seen == null) return grid.animateScrollToItem(item)
        val top = seen.offset.y
        val bottom = top + seen.size.height
        val by = Reveal.by(top, bottom, info.viewportStartOffset, info.viewportEndOffset - info.afterContentPadding)
        if (by != 0) grid.animateScrollBy(by.toFloat())
    }
}

/** How far a view [start] to [end] scrolls so an item [top] to [bottom] shows whole: as little as it takes, its top first when it is taller. */
internal object Reveal {
    fun by(top: Int, bottom: Int, start: Int, end: Int): Int = when {
        top < start -> top - start
        bottom > end -> minOf(bottom - end, top - start)
        else -> 0
    }
}

private const val TAKE_KEYS_DELAY_MS = 150L
