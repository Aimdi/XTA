package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.aimdi.xtanative.ui.chrome.AppClosing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The window's keys and mouse on the way to the screens: the content's frame
 * keeps the focus whenever nothing inside it has it, so the keys always
 * arrive here first.
 */
@Composable
actual fun DesktopNavigation(nav: NavHostController, content: @Composable () -> Unit) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var hasFocus by remember { mutableStateOf(false) }
    var focusInside by remember { mutableStateOf(false) }
    // A frame later: what gives the keys back is still leaving when it asks. The frame takes them only if
    // nothing in the page has since (a screen that focuses its field as it opens keeps it).
    val actions = remember {
        DesktopActions(refocus = { under ->
            scope.launch {
                delay(REFOCUS_AFTER_MS)
                if (under != null || !focusInside) runCatching { (under ?: focus).requestFocus() }
            }
        })
    }
    // What is drawn above this frame (a video full screen) reaches the actions through the root's host.
    val host = LocalDesktopActionsHost.current
    DisposableEffect(host, actions) {
        host.actions = actions
        onDispose { if (host.actions === actions) host.actions = null }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val line = with(LocalDensity.current) { LINE.toPx() }

    fun act(action: DeskAction): Boolean = when (action) {
        DeskAction.LEAVE_FIELD -> {
            focus.requestFocus()
            true
        }
        DeskAction.CLOSE -> {
            actions.close()
            true
        }
        DeskAction.BACK -> nav.popBackStack()
        DeskAction.REFRESH -> actions.refresh()
        DeskAction.PREVIOUS_PAGE -> actions.turn(-1)
        DeskAction.NEXT_PAGE -> actions.turn(1)
        // The frame holds the focus as a whole; Tab's own traversal does not go into it.
        DeskAction.ENTER_PAGE -> focusManager.moveFocus(FocusDirection.Enter)
        DeskAction.LEAVE_APP -> {
            AppClosing.ask()
            true
        }
        DeskAction.LINE_UP, DeskAction.LINE_DOWN, DeskAction.SCREEN_UP, DeskAction.SCREEN_DOWN, DeskAction.TO_TOP, DeskAction.TO_END ->
            actions.readList()?.let { list ->
                scope.launch { list.moveFor(action, line) }
                true
            } ?: false
        DeskAction.NONE -> false
    }

    fun onKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val canGoBack = nav.previousBackStackEntry != null
        val key = deskKey(event.key)
        val action = DesktopKeys.action(key, event.isAltPressed, event.isCtrlPressed, focusInside, canGoBack, rtl, actions.somethingOpen(), event.isShiftPressed)
        // The media viewer's Esc closes it at once, rather than first taking the focus from it as from a field; Ctrl+Q still quits.
        if (actions.overlayHoldsKeys() && action != DeskAction.LEAVE_APP) return false
        return act(action)
    }

    CompositionLocalProvider(LocalDesktopActions provides actions) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(nav) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && event.buttons.isBackPressed) {
                                act(DesktopKeys.mouseBack(nav.previousBackStackEntry != null, actions.somethingOpen()))
                            }
                        }
                    }
                }
                .onPreviewKeyEvent(::onKey)
                .onFocusChanged { state ->
                    hasFocus = state.hasFocus
                    focusInside = state.hasFocus && !state.isFocused
                }
                .focusRequester(focus)
                .focusable(),
        ) {
            content()
        }
    }
    // The frame takes the focus when nothing has it: at the start, and once the screen that had it is gone.
    // Only after the screens change, so a sheet's own field is never robbed of it.
    LaunchedEffect(nav) {
        nav.currentBackStackEntryFlow.collect {
            delay(FOCUS_SETTLE_MS)
            if (!hasFocus) runCatching { focus.requestFocus() }
        }
    }
}

/** Long enough for an overlay that gave the keys back to be gone. */
private const val REFOCUS_AFTER_MS = 50L

/** How far ↑ and ↓ move a list: about a line of a post and a half. */
private val LINE = 64.dp

/** A screen's move leaves this much of what was read in view, as a browser's Page Down does. */
private const val SCREEN_KEPT = 0.12f

/** [action] done to the list, eased as the wheel's own moves are. */
private suspend fun LazyListState.moveFor(action: DeskAction, line: Float) {
    val screen = layoutInfo.viewportSize.height * (1 - SCREEN_KEPT)
    when (action) {
        DeskAction.LINE_UP -> animateScrollBy(-line)
        DeskAction.LINE_DOWN -> animateScrollBy(line)
        DeskAction.SCREEN_UP -> animateScrollBy(-screen)
        DeskAction.SCREEN_DOWN -> animateScrollBy(screen)
        DeskAction.TO_TOP -> scrollToItem(0)
        DeskAction.TO_END -> layoutInfo.totalItemsCount.takeIf { it > 0 }?.let { scrollToItem(it - 1) }
        else -> Unit
    }
}

/** Longer than a screen's slide, by which time a screen that focuses a field has done so. */
private const val FOCUS_SETTLE_MS = 400L

private fun deskKey(key: Key): DeskKey = when (key) {
    Key.Escape -> DeskKey.ESCAPE
    Key.DirectionLeft -> DeskKey.LEFT
    Key.DirectionRight -> DeskKey.RIGHT
    Key.F5 -> DeskKey.F5
    Key.R -> DeskKey.R
    Key.Back -> DeskKey.BACK
    Key.DirectionUp -> DeskKey.UP
    Key.DirectionDown -> DeskKey.DOWN
    Key.PageUp -> DeskKey.PAGE_UP
    Key.PageDown -> DeskKey.PAGE_DOWN
    Key.Spacebar -> DeskKey.SPACE
    Key.MoveHome -> DeskKey.HOME
    Key.MoveEnd -> DeskKey.END
    Key.Tab -> DeskKey.TAB
    Key.Q -> DeskKey.Q
    else -> DeskKey.OTHER
}
