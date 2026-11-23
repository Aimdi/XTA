package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController

/**
 * What the screen on top lets the desktop's keys do (XTA's DesktopRefreshKeys
 * and DesktopPager): F5 and Ctrl+R refresh it, ← and → turn its pages, and
 * going back first closes what it has open. One screen holds them at a time,
 * the one resumed; see [DesktopShortcuts].
 */
class DesktopActions(private val refocus: (FocusRequester?) -> Unit = {}) {
    private var owner: Any? = null
    private var refreshing: (() -> Unit)? = null
    private var turning: ((Int) -> Unit)? = null
    private var open: (() -> Boolean)? = null
    private var closing: (() -> Unit)? = null

    internal fun take(owner: Any, refresh: () -> Unit, turn: (Int) -> Unit, isOpen: () -> Boolean, close: () -> Unit) {
        this.owner = owner
        refreshing = refresh
        turning = turn
        open = isOpen
        closing = close
    }

    internal fun release(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        refreshing = null
        turning = null
        open = null
        closing = null
    }

    /** False when no screen answers. */
    fun refresh(): Boolean = refreshing?.let { it(); true } ?: false

    fun turn(by: Int): Boolean = turning?.let { it(by); true } ?: false

    fun somethingOpen(): Boolean = open?.invoke() ?: false

    fun close() {
        closing?.invoke()
    }

    /** The search fields that Ctrl+F and / go to, the last shown on top ([FindsWithKeys]). */
    private val finders = mutableListOf<Pair<Any, () -> Unit>>()

    internal fun addFinder(owner: Any, find: () -> Unit) {
        finders.removeAll { it.first === owner }
        finders += owner to find
    }

    internal fun removeFinder(owner: Any) {
        finders.removeAll { it.first === owner }
    }

    /** The search field shown on top takes the keyboard; false when the page has none. */
    fun find(): Boolean = finders.lastOrNull()?.second?.let { it(); true } ?: false

    /** The lists on screen, in the order they came, and the one the pointer was last over. */
    private val lists = mutableListOf<LazyListState>()
    private var pointed: LazyListState? = null

    internal fun addList(list: LazyListState) {
        lists += list
    }

    internal fun removeList(list: LazyListState) {
        lists -= list
        if (pointed === list) pointed = null
    }

    internal fun point(list: LazyListState) {
        pointed = list
    }

    /** What is drawn over the screens and holds the keys for its own (the media viewer, a video full screen), the last on top. */
    private val holders = mutableListOf<FocusRequester>()

    internal fun holdKeys(holder: FocusRequester) {
        holders += holder
    }

    /** Whether an overlay holds the keys: its own Esc and arrows then come before the window's. */
    fun overlayHoldsKeys(): Boolean = holders.isNotEmpty()

    /** [holder] going: the keys to the one under it, else back to the window's frame, so they always have a taker. */
    internal fun releaseKeys(holder: FocusRequester) {
        holders -= holder
        refocus(holders.lastOrNull())
    }

    /** The sheets open now ([GivesKeysBack]); one over another hands nothing back as it goes. */
    private var sheets = 0

    internal fun sheetOpened() {
        sheets++
    }

    /**
     * A sheet going: once the last is gone, the keys back to the overlay under
     * it, else the window's frame. One still open (a sheet that opened
     * another) keeps its own field's focus.
     */
    /** Whether a sheet is open now: one opened in place of another as it closed takes back the keys it handed back. */
    internal fun sheetOpen(): Boolean = sheets > 0

    internal fun sheetClosed() {
        sheets = (sheets - 1).coerceAtLeast(0)
        if (sheets == 0) refocus(holders.lastOrNull())
    }

    /** The list the keys move: the one the pointer was last over, else the first on screen (the page's own, before any column beside it). */
    internal fun readList(): LazyListState? = pointed ?: lists.firstOrNull()
}

/**
 * Lets the desktop's keys move [list] (DesktopKeys: ↑ ↓, Page Up and Down,
 * Space, Home, End) while it is on screen, and makes it the one they move
 * while the pointer is over it. Nothing off the desktop.
 */
@Composable
fun Modifier.movedByKeys(list: LazyListState): Modifier {
    val actions = LocalDesktopActions.current ?: return this
    DisposableEffect(actions, list) {
        actions.addList(list)
        onDispose { actions.removeList(list) }
    }
    return pointerInput(actions, list) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Enter || event.type == PointerEventType.Move) actions.point(list)
            }
        }
    }
}

/** The desktop's actions; null off the desktop, where only touch and Android's back exist. */
val LocalDesktopActions = staticCompositionLocalOf<DesktopActions?> { null }

/**
 * Where the window's [DesktopActions] are, for what is drawn above the frame
 * that provides them (a video full screen, over every screen). The app's
 * root provides one; [DesktopNavigation] fills it in on the desktop.
 */
class DesktopActionsHost {
    var actions: DesktopActions? = null
}

val LocalDesktopActionsHost = staticCompositionLocalOf { DesktopActionsHost() }

/**
 * Going back the way a PC does it (XTA's DesktopNavigation): the mouse's back
 * button, Alt+← and Esc go back as Android's back gesture does, the first Esc
 * leaving a text field being typed in, and the screen on top answers
 * [DesktopActions]. Off the desktop it is [content] alone.
 */
@Composable
expect fun DesktopNavigation(nav: NavHostController, openSearch: () -> Unit, content: @Composable () -> Unit)

/**
 * Lets Ctrl+F and / ([DesktopKeys]) go to the screen's own search while it is
 * the one shown: [find] puts the keyboard in its field (or opens its search
 * first). Where no screen shown has one, they open the app's search. Nothing
 * off the desktop.
 */
@Composable
fun FindsWithKeys(find: () -> Unit) {
    val actions = LocalDesktopActions.current ?: return
    val latest by rememberUpdatedState(find)
    val owner = remember { Any() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { actions.addFinder(owner) { latest() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { actions.removeFinder(owner) }
    DisposableEffect(actions) { onDispose { actions.removeFinder(owner) } }
}

/** A search field that Ctrl+F and / put the keyboard in while it is shown ([FindsWithKeys]). Nothing off the desktop. */
@Composable
fun Modifier.foundWithKeys(): Modifier {
    if (LocalDesktopActions.current == null) return this
    val focus = remember { FocusRequester() }
    FindsWithKeys { runCatching { focus.requestFocus() } }
    return focusRequester(focus)
}

/**
 * Lets the screen answer the desktop's keys while it is the one shown: [refresh]
 * for F5 and Ctrl+R, [turn] for ← and →, and [isOpen] with [close] for what
 * going back closes first. Nothing off the desktop.
 */
@Composable
internal fun DesktopShortcuts(refresh: () -> Unit, turn: (Int) -> Unit, isOpen: () -> Boolean, close: () -> Unit) {
    val actions = LocalDesktopActions.current ?: return
    val latestRefresh by rememberUpdatedState(refresh)
    val latestTurn by rememberUpdatedState(turn)
    val latestIsOpen by rememberUpdatedState(isOpen)
    val latestClose by rememberUpdatedState(close)
    val owner = remember { Any() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        actions.take(owner, { latestRefresh() }, { latestTurn(it) }, { latestIsOpen() }, { latestClose() })
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { actions.release(owner) }
    DisposableEffect(actions) { onDispose { actions.release(owner) } }
}

/**
 * For something drawn over the screens that keeps no keys of its own (a
 * sheet): when it goes, the keys go back to what it covered, the overlay
 * under it (a video full screen) or the window's frame, so they never
 * wait for a click. Nothing off the desktop.
 */
@Composable
fun GivesKeysBack() {
    val actions = LocalDesktopActions.current ?: LocalDesktopActionsHost.current.actions ?: return
    DisposableEffect(actions) {
        actions.sheetOpened()
        onDispose { actions.sheetClosed() }
    }
}

/**
 * For something drawn over the screens that takes the keys with [focus] (the
 * media viewer, a video full screen): when it goes, the keys go back to what
 * it covered, the overlay under it or the window's frame. Without that its
 * going leaves nothing with the focus and every desktop key without a
 * taker. Nothing off the desktop.
 */
@Composable
fun HoldsKeys(focus: FocusRequester) {
    val actions = LocalDesktopActions.current ?: LocalDesktopActionsHost.current.actions ?: return
    DisposableEffect(actions, focus) {
        actions.holdKeys(focus)
        onDispose { actions.releaseKeys(focus) }
    }
}
