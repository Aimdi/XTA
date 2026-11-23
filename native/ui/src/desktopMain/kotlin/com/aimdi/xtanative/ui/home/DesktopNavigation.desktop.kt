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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.aimdi.xtanative.store.settings.AccessibilityPrefs
import com.aimdi.xtanative.ui.chrome.AppClosing
import com.aimdi.xtanative.ui.common.LocalSettingsStore
import com.aimdi.xtanative.ui.links.PastedLinks
import com.aimdi.xtanative.ui.links.takesDroppedLinks
import com.aimdi.xtanative.ui.settings.SettingsChoices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The window's keys and mouse on the way to the screens: the content's frame
 * keeps the focus whenever nothing inside it has it, so the keys always
 * arrive here first.
 */
@Composable
actual fun DesktopNavigation(nav: NavHostController, openSearch: () -> Unit, openSettings: () -> Unit, openHome: () -> Unit, content: @Composable () -> Unit) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val settingsStore = LocalSettingsStore.current
    val scope = rememberCoroutineScope()
    var hasFocus by remember { mutableStateOf(false) }
    var focusInside by remember { mutableStateOf(false) }
    // Whether the window took the last key pressed, so the character it types goes nowhere.
    var keyTaken by remember { mutableStateOf(false) }
    var showKeys by remember { mutableStateOf(false) }
    // A frame later: what gives the keys back is still leaving when it asks. The frame takes them only if
    // nothing in the page has since (a screen that focuses its field as it opens keeps it).
    // A sheet swapped for another in the same frame closes before the next opens: by the time the frame
    // would take the keys, the new sheet holds them, so it does not.
    val created = remember { arrayOfNulls<DesktopActions>(1) }
    val actions = remember {
        DesktopActions(refocus = { under ->
            scope.launch {
                delay(REFOCUS_AFTER_MS)
                val sheetOpen = created[0]?.sheetOpen() == true
                if (under != null || (!focusInside && !sheetOpen)) runCatching { (under ?: focus).requestFocus() }
            }
        }).also { created[0] = it }
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
        DeskAction.FIND -> {
            if (!actions.find()) openSearch()
            true
        }
        DeskAction.TEXT_BIGGER, DeskAction.TEXT_SMALLER, DeskAction.TEXT_RESET -> {
            scope.launch {
                settingsStore.update { settings ->
                    settings.with(AccessibilityPrefs.textScale, DesktopKeys.textScaled(settings[AccessibilityPrefs.textScale], action, TEXT_SCALES))
                }
                // A field the new size lays out afresh (Search's, moving under its bar) loses the focus: the keys come back here.
                delay(REFOCUS_AFTER_MS)
                actions.keysToFrame()
            }
            true
        }
        DeskAction.LINE_UP, DeskAction.LINE_DOWN, DeskAction.SCREEN_UP, DeskAction.SCREEN_DOWN, DeskAction.TO_TOP, DeskAction.TO_END,
        DeskAction.NEXT_ITEM, DeskAction.PREVIOUS_ITEM ->
            actions.readList()?.let { list ->
                scope.launch { list.moveFor(action, line) }
                true
            } ?: false
        // Read off the window's thread; Ctrl+V outside a field has nothing else to do, so the key is taken either way.
        DeskAction.OPEN_PASTED_LINK -> {
            scope.launch { PastedLinks.open() }
            true
        }
        DeskAction.GO_HOME -> {
            openHome()
            true
        }
        DeskAction.NEXT_COLUMN -> actions.stepColumn(1)
        DeskAction.PREVIOUS_COLUMN -> actions.stepColumn(-1)
        DeskAction.OPEN_SETTINGS -> {
            openSettings()
            true
        }
        DeskAction.SHOW_KEYS -> {
            showKeys = true
            true
        }
        DeskAction.NONE -> false
    }

    fun onKey(event: KeyEvent): Boolean {
        // The character a key the window took types (Ctrl+= types =) is not typed into a field as well.
        if (event.type != KeyEventType.KeyDown) return event.type != KeyEventType.KeyUp && keyTaken
        // Ctrl and a digit go to that tab, as a browser's do, from a field too; not under something open over the page.
        TAB_DIGITS[event.key]?.let { digit ->
            if (event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && !actions.overlayHoldsKeys() && !actions.somethingOpen()) {
                keyTaken = actions.jump(digit)
                return keyTaken
            }
        }
        val canGoBack = nav.previousBackStackEntry != null
        val key = deskKey(event.key)
        val action = DesktopKeys.action(key, event.isAltPressed, event.isCtrlPressed, focusInside, canGoBack, rtl, actions.somethingOpen(), event.isShiftPressed)
        // A search field's first Esc drops the recent search it highlighted; the next leaves the field.
        if (action == DeskAction.LEAVE_FIELD && actions.escapeInField()) {
            keyTaken = true
            return true
        }
        // The media viewer's Esc closes it at once, rather than first taking the focus from it as from a field; Ctrl+Q still quits.
        keyTaken = !(actions.overlayHoldsKeys() && action != DeskAction.LEAVE_APP) && act(action)
        return keyTaken
    }

    // A / or ? is the character typed, on any layout (Shift+7 on a German one), and only one no field has typed reaches here.
    fun onTyped(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyDown || event.type == KeyEventType.KeyUp) return false
        val key = when (event.utf16CodePoint) {
            '/'.code -> DeskKey.SLASH
            '?'.code -> DeskKey.QUESTION
            else -> return false
        }
        val action = DesktopKeys.action(key, event.isAltPressed, event.isCtrlPressed, focusInside = false, nav.previousBackStackEntry != null, rtl, actions.somethingOpen(), event.isShiftPressed)
        if (actions.overlayHoldsKeys()) return false
        return act(action)
    }

    val frameOrigin = remember { arrayOf(Offset.Zero) }
    CompositionLocalProvider(LocalDesktopActions provides actions) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { frameOrigin[0] = it.positionInWindow() }
                .pointerInput(actions) {
                    // After the lists had the event ([DesktopActions.point]): the pointer's place in the window, from every move.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            if (event.type == PointerEventType.Move || event.type == PointerEventType.Enter) {
                                event.changes.firstOrNull()?.let { actions.pointerAt(frameOrigin[0] + it.position) }
                            }
                        }
                    }
                }
                .pointerInput(nav) {
                    var wheeled = 0f
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && event.buttons.isBackPressed) {
                                act(DesktopKeys.mouseBack(nav.previousBackStackEntry != null, actions.somethingOpen()))
                            }
                            // Ctrl and the wheel size the text, as Ctrl and +/- do; the page under it does not scroll meanwhile.
                            // Over the media viewer the wheel is its own (zoom).
                            if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed && !actions.overlayHoldsKeys()) {
                                val delta = event.changes.fold(0f) { sum, change -> sum + change.scrollDelta.y }
                                val (left, action) = DesktopKeys.wheelText(wheeled, delta)
                                wheeled = left
                                if (action != DeskAction.NONE) act(action)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .takesDroppedLinks()
                .onPreviewKeyEvent(::onKey)
                .onKeyEvent(::onTyped)
                .onFocusChanged { state ->
                    // What had the keys inside the page went away with them (a button that closed what it was on):
                    // the frame takes them back, so they never wait for a click.
                    if (hasFocus && !state.hasFocus) actions.keysToFrame()
                    hasFocus = state.hasFocus
                    focusInside = state.hasFocus && !state.isFocused
                }
                .focusRequester(focus)
                .focusable(),
        ) {
            content()
        }
    }
    if (showKeys) {
        ShortcutsDialog(onDismiss = {
            showKeys = false
            actions.keysToFrame()
        })
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

/** The text sizes the keys move between: the Accessibility slider's. */
private val TEXT_SCALES = SettingsChoices.TEXT_SCALE_MIN.toDouble()..SettingsChoices.TEXT_SCALE_MAX.toDouble()

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
        DeskAction.NEXT_ITEM, DeskAction.PREVIOUS_ITEM ->
            DesktopKeys.itemStep(
                layoutInfo.visibleItemsInfo.map { DesktopKeys.ShownItem(it.index, it.offset, it.size) },
                layoutInfo.totalItemsCount,
                action == DeskAction.NEXT_ITEM,
            )?.let { (index, offset) ->
                animateScrollToItem(index, offset)
                // Tabs in their own place before the step stick on the way, over the post's start: it comes out from under them.
                val covered = DesktopKeys.coveredAfterStep(layoutInfo.visibleItemsInfo.map { DesktopKeys.ShownItem(it.index, it.offset, it.size) }, index)
                if (covered > 0) animateScrollBy(-covered.toFloat())
            }
        else -> Unit
    }
}

/** Longer than a screen's slide, by which time a screen that focuses a field has done so. */
private const val FOCUS_SETTLE_MS = 400L

/** The digit keys Ctrl takes to a tab ([DesktopKeys.pageAt]), on the main keys and the number pad. */
private val TAB_DIGITS: Map<Key, Int> = mapOf(
    Key.One to 1, Key.Two to 2, Key.Three to 3, Key.Four to 4, Key.Five to 5, Key.Six to 6, Key.Seven to 7, Key.Eight to 8, Key.Nine to 9,
    Key.NumPad1 to 1, Key.NumPad2 to 2, Key.NumPad3 to 3, Key.NumPad4 to 4, Key.NumPad5 to 5, Key.NumPad6 to 6, Key.NumPad7 to 7, Key.NumPad8 to 8, Key.NumPad9 to 9,
)

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
    Key.F -> DeskKey.F
    Key.F1 -> DeskKey.F1
    Key.Comma -> DeskKey.COMMA
    Key.W -> DeskKey.W
    Key.J -> DeskKey.J
    Key.V -> DeskKey.V
    Key.K -> DeskKey.K
    Key.Plus, Key.Equals, Key.NumPadAdd -> DeskKey.PLUS
    Key.Minus, Key.NumPadSubtract -> DeskKey.MINUS
    Key.Zero, Key.NumPad0 -> DeskKey.ZERO
    else -> DeskKey.OTHER
}
